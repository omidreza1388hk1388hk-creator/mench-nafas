import {
  BadRequestException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export const REPORT_CATEGORIES = [
  'spam',
  'harassment',
  'violence',
  'sexual',
  'scam',
  'impersonation',
  'other',
] as const;
export type ReportCategory = (typeof REPORT_CATEGORIES)[number];

interface RuleRow {
  key: string;
  enabled: boolean;
  threshold: number;
  window_seconds: number;
  action_hours: number;
}

/**
 * Moderation core: reports, restrictions, appeals, automatic abuse rules and the audit log.
 * Privacy / data minimisation: a reviewer sees ONLY the reported message (not the chat around it),
 * and only the text body; attachments are shown as type + size, never opened.
 */
@Injectable()
export class SafetyService {
  /** In-memory sliding windows for the auto rules. Per-process on purpose: cheap, and a restart only forgives. */
  private readonly sendTimes = new Map<string, number[]>();
  private readonly repeatBodies = new Map<string, Array<{ at: number; body: string }>>();
  private rulesCache: { at: number; rules: RuleRow[] } | null = null;
  /** Restriction lookups are cached for a few seconds so a busy sender does not hit the DB per message. */
  private readonly restrictionCache = new Map<
    string,
    { at: number; value: { kind: string; expiresAt: Date | null } | null }
  >();

  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  // ---------- audit ----------
  async audit(
    actor: string,
    action: string,
    targetType: string,
    targetId: string | null,
    detail: Record<string, unknown> = {},
  ) {
    await this.pool.query(
      'INSERT INTO moderation_audit (actor, action, target_type, target_id, detail) VALUES ($1,$2,$3,$4,$5)',
      [actor, action, targetType, targetId, JSON.stringify(detail)],
    );
  }

  // ---------- sending gate + auto rules ----------
  async activeRestriction(
    userId: string,
  ): Promise<{ kind: string; expiresAt: Date | null } | null> {
    const cached = this.restrictionCache.get(userId);
    if (cached && Date.now() - cached.at < 5000) return cached.value;
    const { rows } = await this.pool.query<{ kind: string; expires_at: Date | null }>(
      `SELECT kind, expires_at FROM user_restrictions
        WHERE user_id = $1 AND lifted_at IS NULL AND (expires_at IS NULL OR expires_at > now())
        ORDER BY (kind = 'suspend') DESC, created_at DESC LIMIT 1`,
      [userId],
    );
    const value = rows[0] ? { kind: rows[0].kind, expiresAt: rows[0].expires_at } : null;
    this.restrictionCache.set(userId, { at: Date.now(), value });
    return value;
  }

  /** Throws 403 if the user is currently muted or suspended; otherwise evaluates the automatic rules. */
  async assertCanSend(userId: string, body?: string | null): Promise<void> {
    const r = await this.activeRestriction(userId);
    if (r)
      throw new ForbiddenException(
        `Your account is temporarily restricted (${r.kind}). You can appeal in Settings.`,
      );
    await this.evaluateRules(userId, body ?? null);
  }

  private async rules(): Promise<RuleRow[]> {
    if (this.rulesCache && Date.now() - this.rulesCache.at < 30_000) return this.rulesCache.rules;
    const { rows } = await this.pool.query<RuleRow>(
      'SELECT key, enabled, threshold, window_seconds, action_hours FROM moderation_rules',
    );
    this.rulesCache = { at: Date.now(), rules: rows };
    return rows;
  }

  private async evaluateRules(userId: string, body: string | null) {
    const now = Date.now();
    const rules = (await this.rules()).filter((r) => r.enabled);
    const flood = rules.find((r) => r.key === 'flood');
    if (flood) {
      const arr = (this.sendTimes.get(userId) ?? []).filter(
        (t) => now - t < flood.window_seconds * 1000,
      );
      arr.push(now);
      this.sendTimes.set(userId, arr);
      if (arr.length > flood.threshold) {
        await this.autoRestrict(
          userId,
          flood,
          arr.length / flood.threshold,
          `${arr.length} messages in ${flood.window_seconds}s`,
        );
      }
    }
    const repeat = rules.find((r) => r.key === 'repeat_message');
    if (repeat && body && body.trim().length >= 4) {
      const normalized = body.trim().toLowerCase();
      const arr = (this.repeatBodies.get(userId) ?? []).filter(
        (e) => now - e.at < repeat.window_seconds * 1000,
      );
      arr.push({ at: now, body: normalized });
      this.repeatBodies.set(userId, arr);
      const same = arr.filter((e) => e.body === normalized).length;
      if (same >= repeat.threshold) {
        await this.autoRestrict(
          userId,
          repeat,
          same / repeat.threshold,
          `same text sent ${same} times`,
        );
      }
    }
  }

  private async autoRestrict(userId: string, rule: RuleRow, ratio: number, evidence: string) {
    const confidence = Math.min(0.99, 0.5 + ratio / 4);
    const expiresAt = new Date(Date.now() + rule.action_hours * 3600_000);
    const { rows } = await this.pool.query<{ id: string }>(
      `INSERT INTO user_restrictions (user_id, kind, reason, source, confidence, expires_at)
       VALUES ($1,'mute',$2,$3,$4,$5) RETURNING id`,
      [userId, `Automatic: ${evidence}`, `rule:${rule.key}`, confidence, expiresAt],
    );
    this.restrictionCache.delete(userId);
    this.sendTimes.delete(userId);
    this.repeatBodies.delete(userId);
    await this.audit(`rule:${rule.key}`, 'auto_mute', 'user', userId, {
      restrictionId: rows[0].id,
      evidence,
      confidence,
      hours: rule.action_hours,
    });
    throw new ForbiddenException(
      'You are sending messages too fast. You were muted temporarily; you can appeal in Settings.',
    );
  }

  // ---------- user-facing ----------
  async createReport(
    reporterId: string,
    dto: {
      targetUserId?: string;
      conversationId?: string;
      messageId?: string;
      category: string;
      details?: string;
    },
  ) {
    if (!REPORT_CATEGORIES.includes(dto.category as ReportCategory))
      throw new BadRequestException('Unknown category');
    if (!dto.targetUserId && !dto.messageId)
      throw new BadRequestException('A report needs a user or a message');
    let targetUserId = dto.targetUserId ?? null;
    let conversationId = dto.conversationId ?? null;
    if (dto.messageId) {
      // Reporter must be a member of the conversation the message lives in.
      const { rows } = await this.pool.query<{ sender_id: string; conversation_id: string }>(
        `SELECT m.sender_id, m.conversation_id FROM messages m
           JOIN conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = $2
          WHERE m.id = $1`,
        [dto.messageId, reporterId],
      );
      if (!rows[0]) throw new NotFoundException('Message not found');
      targetUserId = rows[0].sender_id;
      conversationId = rows[0].conversation_id;
    }
    if (targetUserId === reporterId) throw new BadRequestException('You cannot report yourself');
    const recent = await this.pool.query<{ n: string }>(
      "SELECT count(*) AS n FROM reports WHERE reporter_id = $1 AND created_at > now() - interval '1 hour'",
      [reporterId],
    );
    if (Number(recent.rows[0].n) >= 10)
      throw new ForbiddenException('Too many reports; try again later');
    try {
      const { rows } = await this.pool.query<{ id: string }>(
        `INSERT INTO reports (reporter_id, target_user_id, conversation_id, message_id, category, details)
         VALUES ($1,$2,$3,$4,$5,$6) RETURNING id`,
        [
          reporterId,
          targetUserId,
          conversationId,
          dto.messageId ?? null,
          dto.category,
          dto.details?.slice(0, 1000) ?? null,
        ],
      );
      return { id: rows[0].id };
    } catch (e: any) {
      if (e?.code === '23505') return { id: 'duplicate' }; // already reported by this person; silently idempotent
      throw e;
    }
  }

  async myRestriction(userId: string) {
    const { rows } = await this.pool.query(
      `SELECT r.id, r.kind, r.reason, r.expires_at, r.source,
              (SELECT a.status FROM appeals a WHERE a.restriction_id = r.id ORDER BY a.created_at DESC LIMIT 1) AS appeal_status
         FROM user_restrictions r
        WHERE r.user_id = $1 AND r.lifted_at IS NULL AND (r.expires_at IS NULL OR r.expires_at > now())
        ORDER BY r.created_at DESC LIMIT 1`,
      [userId],
    );
    const r = rows[0];
    if (!r) return { restricted: false };
    return {
      restricted: true,
      id: r.id,
      kind: r.kind,
      reason: r.reason,
      expiresAt: r.expires_at,
      automatic: String(r.source).startsWith('rule:'),
      appealStatus: r.appeal_status ?? null,
    };
  }

  async createAppeal(userId: string, restrictionId: string, message: string) {
    const text = (message ?? '').trim();
    if (text.length < 5 || text.length > 1000)
      throw new BadRequestException('Appeal must be 5-1000 characters');
    const own = await this.pool.query(
      'SELECT 1 FROM user_restrictions WHERE id = $1 AND user_id = $2',
      [restrictionId, userId],
    );
    if (!own.rows[0]) throw new NotFoundException('Restriction not found');
    try {
      await this.pool.query(
        'INSERT INTO appeals (restriction_id, user_id, message) VALUES ($1,$2,$3)',
        [restrictionId, userId, text],
      );
    } catch (e: any) {
      if (e?.code === '23505')
        throw new BadRequestException('You already have an open appeal for this');
      throw e;
    }
  }

  /** Aggregate-only public transparency data. No user ids, no content. */
  async transparency() {
    const byCat = await this.pool.query(
      "SELECT category, count(*)::int AS n FROM reports WHERE created_at > now() - interval '90 days' GROUP BY category ORDER BY n DESC",
    );
    const byStatus = await this.pool.query(
      "SELECT status, count(*)::int AS n FROM reports WHERE created_at > now() - interval '90 days' GROUP BY status",
    );
    const actions = await this.pool.query(
      `SELECT kind, (source LIKE 'rule:%') AS automatic, count(*)::int AS n
         FROM user_restrictions WHERE created_at > now() - interval '90 days' GROUP BY kind, automatic`,
    );
    const appeals = await this.pool.query(
      "SELECT status, count(*)::int AS n FROM appeals WHERE created_at > now() - interval '90 days' GROUP BY status",
    );
    return {
      periodDays: 90,
      reportsByCategory: byCat.rows,
      reportsByStatus: byStatus.rows,
      restrictions: actions.rows,
      appeals: appeals.rows,
      categories: REPORT_CATEGORIES,
      howToReport:
        'Long-press a message and choose Report, or open a chat menu and choose Report user.',
      policies:
        'Spam, harassment, threats, sexual content involving minors, scams and impersonation are not allowed. Automatic limits apply to flooding and repeated messages. Every restriction can be appealed.',
    };
  }

  // ---------- admin ----------
  async adminReports(status: string | undefined, limit: number) {
    const { rows } = await this.pool.query(
      `SELECT r.id, r.category, r.status, r.details, r.created_at, r.target_user_id, r.message_id,
              (SELECT count(*)::int FROM reports x WHERE x.target_user_id = r.target_user_id) AS target_report_count
         FROM reports r WHERE ($1::text IS NULL OR r.status = $1) ORDER BY r.created_at DESC LIMIT $2`,
      [status ?? null, limit],
    );
    return rows;
  }

  async adminReportDetail(id: string, actor: string) {
    const { rows } = await this.pool.query('SELECT * FROM reports WHERE id = $1', [id]);
    const report = rows[0];
    if (!report) throw new NotFoundException();
    let message: Record<string, unknown> | null = null;
    if (report.message_id) {
      const m = await this.pool.query(
        `SELECT m.body, m.kind, m.created_at, a.mime_type, a.size_bytes
           FROM messages m LEFT JOIN attachments a ON a.id = m.attachment_id WHERE m.id = $1`,
        [report.message_id],
      );
      const r = m.rows[0];
      if (r)
        message = {
          text: r.body,
          kind: r.kind,
          createdAt: r.created_at,
          attachment: r.mime_type ? { mime: r.mime_type, sizeBytes: Number(r.size_bytes) } : null,
        };
    }
    const notes = await this.pool.query(
      'SELECT author, note, created_at FROM report_notes WHERE report_id = $1 ORDER BY created_at',
      [id],
    );
    const history = report.target_user_id
      ? (
          await this.pool.query(
            'SELECT id, category, status, created_at FROM reports WHERE target_user_id = $1 AND id <> $2 ORDER BY created_at DESC LIMIT 20',
            [report.target_user_id, id],
          )
        ).rows
      : [];
    await this.audit(actor, 'view_report', 'report', id, { viewedMessage: !!message });
    return { report, message, notes: notes.rows, targetHistory: history };
  }

  async adminResolveReport(
    id: string,
    actor: string,
    status: 'reviewing' | 'actioned' | 'dismissed',
    note?: string,
  ) {
    await this.pool.query(
      `UPDATE reports SET status = $2, resolved_at = CASE WHEN $2 IN ('actioned','dismissed') THEN now() ELSE NULL END,
                          resolved_by = CASE WHEN $2 IN ('actioned','dismissed') THEN $3 ELSE NULL END WHERE id = $1`,
      [id, status, actor],
    );
    if (note?.trim())
      await this.pool.query(
        'INSERT INTO report_notes (report_id, author, note) VALUES ($1,$2,$3)',
        [id, actor, note.trim().slice(0, 2000)],
      );
    await this.audit(actor, `report_${status}`, 'report', id, { note: note ? true : false });
  }

  async adminRestrict(
    userId: string,
    actor: string,
    kind: 'mute' | 'suspend',
    hours: number | null,
    reason: string,
  ) {
    const expiresAt = hours ? new Date(Date.now() + hours * 3600_000) : null;
    const { rows } = await this.pool.query<{ id: string }>(
      `INSERT INTO user_restrictions (user_id, kind, reason, source, expires_at) VALUES ($1,$2,$3,$4,$5) RETURNING id`,
      [userId, kind, reason.slice(0, 500), `manual:${actor}`, expiresAt],
    );
    this.restrictionCache.delete(userId);
    if (kind === 'suspend') {
      // A suspension also ends every active session so the person is signed out everywhere.
      await this.pool.query('DELETE FROM sessions WHERE user_id = $1', [userId]);
    }
    await this.audit(actor, `restrict_${kind}`, 'user', userId, {
      restrictionId: rows[0].id,
      hours,
      reason,
    });
    return { id: rows[0].id };
  }

  async adminLift(restrictionId: string, actor: string) {
    const { rows } = await this.pool.query<{ user_id: string }>(
      'UPDATE user_restrictions SET lifted_at = now() WHERE id = $1 AND lifted_at IS NULL RETURNING user_id',
      [restrictionId],
    );
    if (rows[0]) this.restrictionCache.delete(rows[0].user_id);
    await this.audit(actor, 'lift_restriction', 'restriction', restrictionId);
  }

  async adminRestrictions(userId: string | undefined) {
    const { rows } = await this.pool.query(
      `SELECT id, user_id, kind, reason, source, confidence, expires_at, lifted_at, created_at FROM user_restrictions
        WHERE ($1::uuid IS NULL OR user_id = $1) ORDER BY created_at DESC LIMIT 100`,
      [userId ?? null],
    );
    return rows;
  }

  async adminAppeals(status: string | undefined) {
    const { rows } = await this.pool.query(
      `SELECT a.id, a.restriction_id, a.user_id, a.message, a.status, a.created_at, r.reason, r.source, r.confidence
         FROM appeals a JOIN user_restrictions r ON r.id = a.restriction_id
        WHERE ($1::text IS NULL OR a.status = $1) ORDER BY a.created_at DESC LIMIT 100`,
      [status ?? null],
    );
    return rows;
  }

  async adminResolveAppeal(id: string, actor: string, overturn: boolean) {
    const { rows } = await this.pool.query<{ restriction_id: string }>(
      `UPDATE appeals SET status = $2, resolved_by = $3, resolved_at = now() WHERE id = $1 AND status = 'open' RETURNING restriction_id`,
      [id, overturn ? 'overturned' : 'upheld', actor],
    );
    if (!rows[0]) throw new NotFoundException('Open appeal not found');
    if (overturn) await this.adminLift(rows[0].restriction_id, actor);
    await this.audit(actor, overturn ? 'appeal_overturned' : 'appeal_upheld', 'appeal', id);
  }

  async adminRules() {
    return (await this.pool.query('SELECT * FROM moderation_rules ORDER BY key')).rows;
  }

  async adminSaveRule(
    actor: string,
    r: {
      key: string;
      enabled: boolean;
      threshold: number;
      windowSeconds: number;
      actionHours: number;
    },
  ) {
    await this.pool.query(
      `UPDATE moderation_rules SET enabled = $2, threshold = $3, window_seconds = $4, action_hours = $5 WHERE key = $1`,
      [r.key, r.enabled, r.threshold, r.windowSeconds, r.actionHours],
    );
    this.rulesCache = null;
    await this.audit(actor, 'update_rule', 'rule', r.key, r);
  }

  async adminAudit(limit: number) {
    return (
      await this.pool.query(
        'SELECT actor, action, target_type, target_id, detail, created_at FROM moderation_audit ORDER BY created_at DESC LIMIT $1',
        [limit],
      )
    ).rows;
  }
}
