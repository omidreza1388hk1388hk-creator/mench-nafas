import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { randomBytes } from 'crypto';
import { PG_POOL } from '../../database/database.module';

export interface PublicUserRow {
  id: string;
  display_name: string | null;
  username: string | null;
  bio: string | null;
  avatar_version: string | null;
  phone_e164: string;
}

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no 0/O/1/I

function makeInviteCode(): string {
  const bytes = randomBytes(8);
  let out = '';
  for (let i = 0; i < 8; i++) out += CODE_ALPHABET[bytes[i] % CODE_ALPHABET.length];
  return out;
}

@Injectable()
export class AccountRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  // ---- invite codes ----
  async getOrCreateInviteCode(userId: string): Promise<string> {
    const existing = await this.pool.query<{ invite_code: string | null }>('SELECT invite_code FROM users WHERE id = $1', [userId]);
    if (existing.rows[0]?.invite_code) return existing.rows[0].invite_code;
    for (let attempt = 0; attempt < 5; attempt++) {
      const code = makeInviteCode();
      try {
        const { rows } = await this.pool.query<{ invite_code: string }>(
          'UPDATE users SET invite_code = $2 WHERE id = $1 AND invite_code IS NULL RETURNING invite_code',
          [userId, code],
        );
        if (rows[0]) return rows[0].invite_code;
        const again = await this.pool.query<{ invite_code: string }>('SELECT invite_code FROM users WHERE id = $1', [userId]);
        if (again.rows[0]?.invite_code) return again.rows[0].invite_code;
      } catch (e: any) {
        if (e?.code !== '23505') throw e; // unique violation: collision, try another code
      }
    }
    throw new Error('Could not allocate an invite code');
  }

  async findByInviteCode(code: string): Promise<PublicUserRow | null> {
    const { rows } = await this.pool.query<PublicUserRow>(
      `SELECT id, display_name, username, bio, avatar_version, phone_e164
         FROM users WHERE invite_code = $1 AND deleted_at IS NULL`,
      [code.toUpperCase()],
    );
    return rows[0] ?? null;
  }

  async findByUsername(username: string): Promise<PublicUserRow | null> {
    const { rows } = await this.pool.query<PublicUserRow>(
      `SELECT id, display_name, username, bio, avatar_version, phone_e164
         FROM users WHERE lower(username) = lower($1) AND deleted_at IS NULL`,
      [username],
    );
    return rows[0] ?? null;
  }

  // ---- blocks ----
  async block(blockerId: string, blockedId: string): Promise<void> {
    await this.pool.query(
      'INSERT INTO user_blocks (blocker_id, blocked_id) VALUES ($1, $2) ON CONFLICT DO NOTHING',
      [blockerId, blockedId],
    );
  }

  async unblock(blockerId: string, blockedId: string): Promise<void> {
    await this.pool.query('DELETE FROM user_blocks WHERE blocker_id = $1 AND blocked_id = $2', [blockerId, blockedId]);
  }

  async listBlocked(blockerId: string): Promise<Array<PublicUserRow & { blocked_at: Date }>> {
    const { rows } = await this.pool.query(
      `SELECT u.id, u.display_name, u.username, u.bio, u.avatar_version, u.phone_e164, b.created_at AS blocked_at
         FROM user_blocks b JOIN users u ON u.id = b.blocked_id
        WHERE b.blocker_id = $1 ORDER BY b.created_at DESC`,
      [blockerId],
    );
    return rows;
  }

  /** True when `senderId` is blocked by the other person in a two-member (direct) conversation. */
  async isSenderBlockedInDirect(conversationId: string, senderId: string): Promise<boolean> {
    const { rows } = await this.pool.query<{ blocked: boolean }>(
      `SELECT EXISTS (
         SELECT 1 FROM conversation_members m
           JOIN user_blocks b ON b.blocker_id = m.user_id AND b.blocked_id = $2
          WHERE m.conversation_id = $1 AND m.user_id <> $2
            AND (SELECT count(*) FROM conversation_members WHERE conversation_id = $1) = 2
       ) AS blocked`,
      [conversationId, senderId],
    );
    return rows[0]?.blocked === true;
  }

  // ---- export / deletion ----
  async exportData(userId: string): Promise<Record<string, unknown>> {
    const profile = await this.pool.query(
      'SELECT id, phone_e164, display_name, username, bio, created_at FROM users WHERE id = $1',
      [userId],
    );
    const conversations = await this.pool.query(
      `SELECT c.id, c.created_at, m.role, m.joined_at
         FROM conversation_members m JOIN conversations c ON c.id = m.conversation_id WHERE m.user_id = $1`,
      [userId],
    );
    const messages = await this.pool.query(
      `SELECT id, conversation_id, kind, body, created_at, edited_at, deleted_at
         FROM messages WHERE sender_id = $1 ORDER BY created_at ASC`,
      [userId],
    );
    const blocks = await this.pool.query('SELECT blocked_id, created_at FROM user_blocks WHERE blocker_id = $1', [userId]);
    const saved = await this.pool.query('SELECT kind, body, folder, tags, source_sender_name, source_created_at, created_at FROM saved_items WHERE user_id = $1 ORDER BY created_at', [userId]);
    const planner = await this.pool.query('SELECT kind, title, note, due_at, repeat, location, checklist, completed_at, created_at FROM planner_items WHERE user_id = $1 ORDER BY created_at', [userId]);
    return {
      exportedAt: new Date().toISOString(),
      note: 'Contains your profile, conversation memberships and the messages YOU sent. Other people\'s messages are not included.',
      profile: profile.rows[0] ?? null,
      conversations: conversations.rows,
      messagesSent: messages.rows,
      blockedUsers: blocks.rows,
      savedMessages: saved.rows,
      remindersAndTasks: planner.rows,
    };
  }

  /**
   * Anonymizes the account instead of hard-deleting rows other people's chat history still
   * references (messages.sender_id has no cascade). The phone number is freed so the person
   * can register again later; everything personally identifying is cleared.
   */
  async deleteAccount(userId: string): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query('DELETE FROM sessions WHERE user_id = $1', [userId]);
      await client.query('DELETE FROM devices WHERE user_id = $1', [userId]);
      await client.query('DELETE FROM user_blocks WHERE blocker_id = $1 OR blocked_id = $1', [userId]);
      // Phase 1D personal data: saved messages, planner items, Plus entitlement and referral links.
      await client.query('DELETE FROM saved_items WHERE user_id = $1', [userId]);
      await client.query('DELETE FROM planner_items WHERE user_id = $1', [userId]);
      await client.query('DELETE FROM entitlements WHERE user_id = $1', [userId]);
      await client.query('DELETE FROM referrals WHERE inviter_id = $1 OR invitee_id = $1', [userId]);
      await client.query('DELETE FROM conversation_members WHERE user_id = $1', [userId]);
      await client.query(
        `UPDATE users SET phone_e164 = 'deleted:' || id::text, display_name = NULL, username = NULL,
                bio = NULL, avatar_url = NULL, avatar_version = NULL, invite_code = NULL,
                deleted_at = now(), updated_at = now()
          WHERE id = $1`,
        [userId],
      );
      await client.query('COMMIT');
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }

  // ---- feedback / config / analytics ----
  async addFeedback(f: { userId: string | null; category: string; message: string; appVersion?: string; deviceInfo?: string; logs?: string }): Promise<void> {
    await this.pool.query(
      'INSERT INTO feedback (user_id, category, message, app_version, device_info, logs) VALUES ($1,$2,$3,$4,$5,$6)',
      [f.userId, f.category, f.message, f.appVersion ?? null, f.deviceInfo ?? null, f.logs ?? null],
    );
  }

  async activeAnnouncements() {
    const { rows } = await this.pool.query(
      `SELECT id, title, body, level FROM announcements
        WHERE active AND starts_at <= now() AND (ends_at IS NULL OR ends_at > now())
        ORDER BY created_at DESC LIMIT 5`,
    );
    return rows as Array<{ id: string; title: string; body: string; level: string }>;
  }

  async allFlags() {
    const { rows } = await this.pool.query('SELECT key, enabled, rollout_pct, description FROM feature_flags ORDER BY key');
    return rows as Array<{ key: string; enabled: boolean; rollout_pct: number; description: string | null }>;
  }

  async countEvent(name: string): Promise<void> {
    await this.pool.query(
      `INSERT INTO analytics_counters (day, name, count) VALUES (CURRENT_DATE, $1, 1)
       ON CONFLICT (day, name) DO UPDATE SET count = analytics_counters.count + 1`,
      [name],
    );
  }

  // ---- admin ----
  async adminStats() {
    const q = async (sql: string) => Number((await this.pool.query<{ n: string }>(sql)).rows[0]?.n ?? 0);
    return {
      users: await q('SELECT count(*) AS n FROM users WHERE deleted_at IS NULL'),
      usersLast7d: await q("SELECT count(*) AS n FROM users WHERE created_at > now() - interval '7 days' AND deleted_at IS NULL"),
      conversations: await q('SELECT count(*) AS n FROM conversations'),
      messages: await q('SELECT count(*) AS n FROM messages'),
      messagesLast24h: await q("SELECT count(*) AS n FROM messages WHERE created_at > now() - interval '24 hours'"),
      blocks: await q('SELECT count(*) AS n FROM user_blocks'),
      feedback: await q('SELECT count(*) AS n FROM feedback'),
    };
  }

  async adminUsers(search: string | undefined, limit: number) {
    const { rows } = await this.pool.query(
      `SELECT id, phone_e164, display_name, username, created_at, deleted_at FROM users
        WHERE ($1::text IS NULL OR phone_e164 ILIKE '%'||$1||'%' OR display_name ILIKE '%'||$1||'%' OR username ILIKE '%'||$1||'%')
        ORDER BY created_at DESC LIMIT $2`,
      [search ?? null, limit],
    );
    return rows;
  }

  async adminFeedback(limit: number) {
    const { rows } = await this.pool.query(
      'SELECT id, user_id, category, message, app_version, device_info, created_at, (logs IS NOT NULL) AS has_logs FROM feedback ORDER BY created_at DESC LIMIT $1',
      [limit],
    );
    return rows;
  }

  async adminListAnnouncements() {
    const { rows } = await this.pool.query('SELECT * FROM announcements ORDER BY created_at DESC LIMIT 50');
    return rows;
  }

  async adminSaveAnnouncement(a: { title: string; body: string; level: string; endsAt?: string | null }) {
    const { rows } = await this.pool.query(
      'INSERT INTO announcements (title, body, level, ends_at) VALUES ($1,$2,$3,$4) RETURNING *',
      [a.title, a.body, a.level, a.endsAt ?? null],
    );
    return rows[0];
  }

  async adminSetAnnouncementActive(id: string, active: boolean) {
    await this.pool.query('UPDATE announcements SET active = $2 WHERE id = $1', [id, active]);
  }

  async adminUpsertFlag(f: { key: string; enabled: boolean; rolloutPct: number; description?: string }) {
    await this.pool.query(
      `INSERT INTO feature_flags (key, enabled, rollout_pct, description) VALUES ($1,$2,$3,$4)
       ON CONFLICT (key) DO UPDATE SET enabled = $2, rollout_pct = $3, description = COALESCE($4, feature_flags.description), updated_at = now()`,
      [f.key, f.enabled, f.rolloutPct, f.description ?? null],
    );
  }

  async adminAnalytics(days: number) {
    const { rows } = await this.pool.query(
      `SELECT day::text, name, count::int FROM analytics_counters WHERE day > CURRENT_DATE - $1::int ORDER BY day DESC, name`,
      [days],
    );
    return rows;
  }
}
