import { BadRequestException, ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { Pool } from 'pg';
import { randomBytes } from 'crypto';
import { PG_POOL } from '../../database/database.module';
import { AccountService } from '../account/account.service';

const PUBLIC_LINK_BASE = process.env.PUBLIC_LINK_BASE ?? 'https://mench.app';

/** Referrals + rewards, MENCH Plus entitlements (server-authoritative), redeem codes and the sticker store. */
@Injectable()
export class GrowthService {
  constructor(
    @Inject(PG_POOL) private readonly pool: Pool,
    private readonly account: AccountService,
  ) {}

  // ================= Plus =================
  async plusStatus(userId: string) {
    const { rows } = await this.pool.query<{ expires_at: Date; source: string }>(
      'SELECT expires_at, source FROM entitlements WHERE user_id = $1',
      [userId],
    );
    const e = rows[0];
    const active = !!e && e.expires_at.getTime() > Date.now();
    return {
      active,
      expiresAt: e?.expires_at ?? null,
      source: e?.source ?? null,
      // Clients cache this and keep honouring it offline until `graceUntil`; the server stays the authority.
      graceUntil: active ? new Date(e!.expires_at.getTime() + 3 * 86400_000).toISOString() : null,
      perks: ['premium_stickers', 'exclusive_themes', 'extra_cloud_storage', 'extra_customization'],
      cloudQuotaBytes: active ? 2 * 1024 ** 3 : 200 * 1024 ** 2,
    };
  }

  async grantPlusDays(userId: string, days: number, source: string) {
    await this.pool.query(
      `INSERT INTO entitlements (user_id, expires_at, source)
       VALUES ($1, now() + make_interval(days => $2), $3)
       ON CONFLICT (user_id) DO UPDATE SET
         expires_at = GREATEST(entitlements.expires_at, now()) + make_interval(days => $2),
         source = $3, updated_at = now()`,
      [userId, days, source],
    );
  }

  async redeem(userId: string, rawCode: string) {
    const code = (rawCode ?? '').trim().toUpperCase();
    if (!/^[A-Z0-9-]{4,32}$/.test(code)) throw new BadRequestException('Invalid code');
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const { rows } = await client.query<{ days: number; max_uses: number; used_count: number; expires_at: Date | null }>(
        'SELECT days, max_uses, used_count, expires_at FROM redeem_codes WHERE code = $1 FOR UPDATE',
        [code],
      );
      const c = rows[0];
      if (!c || (c.expires_at && c.expires_at.getTime() < Date.now()) || c.used_count >= c.max_uses) {
        throw new BadRequestException('Code is invalid, expired or already used up');
      }
      const dup = await client.query('SELECT 1 FROM redeem_uses WHERE code = $1 AND user_id = $2', [code, userId]);
      if (dup.rows[0]) throw new BadRequestException('You already used this code');
      await client.query('INSERT INTO redeem_uses (code, user_id) VALUES ($1,$2)', [code, userId]);
      await client.query('UPDATE redeem_codes SET used_count = used_count + 1 WHERE code = $1', [code]);
      await client.query(
        `INSERT INTO entitlements (user_id, expires_at, source) VALUES ($1, now() + make_interval(days => $2), 'redeem')
         ON CONFLICT (user_id) DO UPDATE SET expires_at = GREATEST(entitlements.expires_at, now()) + make_interval(days => $2), source = 'redeem', updated_at = now()`,
        [userId, c.days],
      );
      await client.query('COMMIT');
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
    return this.plusStatus(userId);
  }

  async stickerPacks(userId: string) {
    const plus = (await this.plusStatus(userId)).active;
    const { rows } = await this.pool.query('SELECT id, name, premium, glyphs FROM sticker_packs WHERE active ORDER BY sort, name');
    return rows.map((p) => ({ id: p.id, name: p.name, premium: p.premium, locked: p.premium && !plus, glyphs: p.premium && !plus ? (p.glyphs as string[]).slice(0, 3) : p.glyphs }));
  }

  // ================= Referrals =================
  private async config() {
    return (await this.pool.query('SELECT * FROM referral_config WHERE id')).rows[0];
  }

  /** The new user enters/gets an inviter's code once. Self-referral and old accounts are rejected. */
  async claim(inviteeId: string, rawCode: string) {
    const cfg = await this.config();
    if (!cfg.enabled) throw new ForbiddenException('Referrals are not active');
    const inviter = await this.account.resolveInvite((rawCode ?? '').trim());
    if (inviter.id === inviteeId) throw new BadRequestException('You cannot use your own code');
    const me = await this.pool.query<{ created_at: Date }>('SELECT created_at FROM users WHERE id = $1 AND deleted_at IS NULL', [inviteeId]);
    if (!me.rows[0]) throw new NotFoundException();
    if (Date.now() - me.rows[0].created_at.getTime() > cfg.max_account_age_days * 86400_000) {
      throw new ForbiddenException('Invite codes can only be used within the first days of a new account');
    }
    try {
      await this.pool.query('INSERT INTO referrals (inviter_id, invitee_id) VALUES ($1,$2)', [inviter.id, inviteeId]);
    } catch (e: any) {
      if (e?.code === '23505') throw new BadRequestException('You already used an invite code');
      throw e;
    }
  }

  /** Promotes pending referrals whose invitee became genuinely active, and pays the reward once. */
  private async qualifyFor(inviterId: string) {
    const cfg = await this.config();
    if (!cfg.enabled) return;
    const pending = await this.pool.query<{ id: string; invitee_id: string }>(
      "SELECT id, invitee_id FROM referrals WHERE inviter_id = $1 AND status = 'pending'",
      [inviterId],
    );
    for (const ref of pending.rows) {
      const stats = await this.pool.query<{ msgs: string; convs: string }>(
        'SELECT count(*) AS msgs, count(DISTINCT conversation_id) AS convs FROM messages WHERE sender_id = $1 AND deleted_at IS NULL',
        [ref.invitee_id],
      );
      if (Number(stats.rows[0].msgs) < cfg.qualify_messages || Number(stats.rows[0].convs) < cfg.qualify_conversations) continue;
      const rewarded = await this.pool.query<{ n: string }>("SELECT count(*) AS n FROM referrals WHERE inviter_id = $1 AND status = 'rewarded'", [inviterId]);
      if (Number(rewarded.rows[0].n) >= cfg.max_rewards_per_user) {
        await this.pool.query("UPDATE referrals SET status = 'qualified', qualified_at = now() WHERE id = $1", [ref.id]);
        continue;
      }
      await this.grantPlusDays(inviterId, cfg.reward_plus_days, 'referral');
      await this.pool.query("UPDATE referrals SET status = 'rewarded', qualified_at = now() WHERE id = $1", [ref.id]);
    }
  }

  async myReferrals(userId: string) {
    await this.qualifyFor(userId);
    const cfg = await this.config();
    const invite = await this.account.myInvite(userId);
    const { rows } = await this.pool.query(
      `SELECT r.status, r.created_at, r.qualified_at, left(coalesce(u.display_name, ''), 1) AS initial
         FROM referrals r JOIN users u ON u.id = r.invitee_id WHERE r.inviter_id = $1 ORDER BY r.created_at DESC LIMIT 100`,
      [userId],
    );
    return {
      code: invite.code,
      link: `${PUBLIC_LINK_BASE}/i/${invite.code}`,
      rewardsEnabled: cfg.enabled,
      rule: { qualifyMessages: cfg.qualify_messages, qualifyConversations: cfg.qualify_conversations, rewardPlusDays: cfg.reward_plus_days, maxRewards: cfg.max_rewards_per_user },
      stats: {
        invited: rows.length,
        rewarded: rows.filter((r) => r.status === 'rewarded').length,
        pending: rows.filter((r) => r.status === 'pending').length,
      },
      // Privacy: only the first letter of the friend's name, never phone or full name.
      history: rows.map((r) => ({ status: r.status, createdAt: r.created_at, qualifiedAt: r.qualified_at, initial: r.initial || '?' })),
    };
  }

  // ================= admin =================
  async adminReferralConfig() { return this.config(); }

  async adminSaveReferralConfig(c: { enabled: boolean; qualifyMessages: number; qualifyConversations: number; rewardPlusDays: number; maxRewardsPerUser: number }) {
    await this.pool.query(
      `UPDATE referral_config SET enabled=$1, qualify_messages=$2, qualify_conversations=$3, reward_plus_days=$4, max_rewards_per_user=$5 WHERE id`,
      [c.enabled, c.qualifyMessages, c.qualifyConversations, c.rewardPlusDays, c.maxRewardsPerUser],
    );
  }

  async adminGrantPlus(userId: string, days: number) {
    await this.grantPlusDays(userId, days, 'admin');
  }

  async adminCreateCodes(days: number, count: number, maxUses: number, expiresAt?: string) {
    const out: string[] = [];
    for (let i = 0; i < count; i++) {
      const code = randomBytes(5).toString('hex').toUpperCase();
      await this.pool.query('INSERT INTO redeem_codes (code, days, max_uses, expires_at) VALUES ($1,$2,$3,$4)', [code, days, maxUses, expiresAt ?? null]);
      out.push(code);
    }
    return out;
  }

  async adminSavePack(p: { id?: string; name: string; premium: boolean; active: boolean; sort: number; glyphs: string[] }) {
    if (p.id) {
      await this.pool.query('UPDATE sticker_packs SET name=$2, premium=$3, active=$4, sort=$5, glyphs=$6 WHERE id=$1', [p.id, p.name, p.premium, p.active, p.sort, p.glyphs]);
    } else {
      await this.pool.query('INSERT INTO sticker_packs (name, premium, active, sort, glyphs) VALUES ($1,$2,$3,$4,$5)', [p.name, p.premium, p.active, p.sort, p.glyphs]);
    }
  }

  async adminPacks() { return (await this.pool.query('SELECT * FROM sticker_packs ORDER BY sort, name')).rows; }
}
