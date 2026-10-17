import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';
import { DEFAULT_PRIVACY, PrivacySettings } from './privacy.types';

interface PrivacyRow {
  last_seen: 'everybody' | 'nobody';
  profile_photo: 'everybody' | 'nobody';
  add_me: 'everybody' | 'nobody';
  calls: 'everybody' | 'nobody';
  read_receipts: boolean;
}

function toSettings(row: PrivacyRow): PrivacySettings {
  return {
    lastSeen: row.last_seen,
    profilePhoto: row.profile_photo,
    addMe: row.add_me,
    calls: row.calls,
    readReceipts: row.read_receipts,
  };
}

@Injectable()
export class PrivacyRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async get(userId: string): Promise<PrivacySettings> {
    const { rows } = await this.pool.query<PrivacyRow>(
      'SELECT * FROM user_privacy WHERE user_id = $1',
      [userId],
    );
    return rows[0] ? toSettings(rows[0]) : { ...DEFAULT_PRIVACY };
  }

  /** Upsert of the merged settings; callers pass the full object so the row is always complete. */
  async save(userId: string, s: PrivacySettings): Promise<void> {
    await this.pool.query(
      `INSERT INTO user_privacy (user_id, last_seen, profile_photo, add_me, calls, read_receipts)
       VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (user_id) DO UPDATE SET
         last_seen = EXCLUDED.last_seen,
         profile_photo = EXCLUDED.profile_photo,
         add_me = EXCLUDED.add_me,
         calls = EXCLUDED.calls,
         read_receipts = EXCLUDED.read_receipts,
         updated_at = now()`,
      [userId, s.lastSeen, s.profilePhoto, s.addMe, s.calls, s.readReceipts],
    );
  }

  /** Conversation role lookup for the disappearing-timer permission check. */
  async membership(
    conversationId: string,
    userId: string,
  ): Promise<{ kind: 'direct' | 'group'; role: 'owner' | 'member' } | null> {
    const { rows } = await this.pool.query<{ kind: 'direct' | 'group'; role: 'owner' | 'member' }>(
      `SELECT c.kind, cm.role
         FROM conversations c
         JOIN conversation_members cm ON cm.conversation_id = c.id
        WHERE c.id = $1 AND cm.user_id = $2`,
      [conversationId, userId],
    );
    return rows[0] ?? null;
  }

  async getDisappearSeconds(conversationId: string): Promise<number | null> {
    const { rows } = await this.pool.query<{ disappear_after_seconds: number | null }>(
      'SELECT disappear_after_seconds FROM conversations WHERE id = $1',
      [conversationId],
    );
    return rows[0]?.disappear_after_seconds ?? null;
  }

  async setDisappearSeconds(conversationId: string, seconds: number | null): Promise<void> {
    await this.pool.query('UPDATE conversations SET disappear_after_seconds = $2 WHERE id = $1', [
      conversationId,
      seconds,
    ]);
  }

  async memberIds(conversationId: string): Promise<string[]> {
    const { rows } = await this.pool.query<{ user_id: string }>(
      'SELECT user_id FROM conversation_members WHERE conversation_id = $1',
      [conversationId],
    );
    return rows.map((r) => r.user_id);
  }

  /**
   * Marks due messages as deleted (same state as a user delete, so every client already renders it)
   * and returns them. FOR UPDATE SKIP LOCKED keeps two backend instances from sweeping the same rows.
   */
  async sweepExpired(
    limit: number,
  ): Promise<Array<{ id: string; conversation_id: string; deleted_at: Date }>> {
    const { rows } = await this.pool.query<{
      id: string;
      conversation_id: string;
      deleted_at: Date;
    }>(
      `WITH due AS (
         SELECT id FROM messages
          WHERE expires_at IS NOT NULL AND expires_at <= now() AND deleted_at IS NULL
          ORDER BY expires_at
          LIMIT $1
          FOR UPDATE SKIP LOCKED
       )
       UPDATE messages m SET deleted_at = now()
         FROM due
        WHERE m.id = due.id
        RETURNING m.id, m.conversation_id, m.deleted_at`,
      [limit],
    );
    return rows;
  }
}
