import { Inject, Injectable } from '@nestjs/common';
import { Pool, PoolClient } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface CallRow {
  id: string;
  conversation_id: string;
  caller_id: string;
  callee_id: string;
  call_type: string;
  status: string;
  end_reason: string | null;
  started_at: Date;
  answered_at: Date | null;
  ended_at: Date | null;
}

export interface CallHistoryRow extends CallRow {
  other_user_id: string;
  other_phone_e164: string;
  other_display_name: string | null;
}

/**
 * Lives in its own module (CallsRepositoryModule, see calls-repository.module.ts)
 * separate from CallsService/CallsController, specifically so ChatGateway
 * (in RealtimeModule) can depend on this repository directly for
 * signaling-relay authorization without RealtimeModule importing the full
 * CallsModule — which itself needs to import RealtimeModule for the
 * REALTIME_BROADCASTER it uses to push call.incoming/accepted/etc. That
 * pairing (RealtimeModule -> CallsModule -> RealtimeModule) would be a
 * real circular dependency; this repository-only module breaks the cycle
 * the same way SessionsModule/ConversationsModule already do for other
 * cross-module reads. See docs/ARCHITECTURE.md.
 */
@Injectable()
export class CallsRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(
    conversationId: string,
    callerId: string,
    calleeId: string,
    callType: 'voice' | 'video',
  ): Promise<CallRow> {
    const client: PoolClient = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const { rows } = await client.query<CallRow>(
        `INSERT INTO calls (conversation_id, caller_id, callee_id, call_type, status)
         VALUES ($1, $2, $3, $4, 'ringing') RETURNING *`,
        [conversationId, callerId, calleeId, callType],
      );
      const call = rows[0];
      await client.query(
        `INSERT INTO call_participants (call_id, user_id, joined_at) VALUES ($1, $2, now())`,
        [call.id, callerId],
      );
      // Callee's own joined_at is set only when they actually accept
      // (see markAccepted) — a row still exists for them from the start
      // so call_participants always has exactly two rows per call, but a
      // NULL joined_at is what distinguishes "was invited" from "was
      // actually connected", useful for future call-quality/analytics
      // work without touching this schema again.
      await client.query(
        `INSERT INTO call_participants (call_id, user_id) VALUES ($1, $2)`,
        [call.id, calleeId],
      );
      await client.query('COMMIT');
      return call;
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async findById(callId: string): Promise<CallRow | null> {
    const { rows } = await this.pool.query<CallRow>(`SELECT * FROM calls WHERE id = $1`, [callId]);
    return rows[0] ?? null;
  }

  /** Used by ChatGateway to authorize relaying a signaling frame — true for both caller and callee, no one else. */
  async isParticipant(callId: string, userId: string): Promise<boolean> {
    const { rows } = await this.pool.query(
      `SELECT 1 FROM calls WHERE id = $1 AND (caller_id = $2 OR callee_id = $2)`,
      [callId, userId],
    );
    return rows.length > 0;
  }

  /** The id of the other party on this call — the sole relay target for every signaling frame (1:1 calls only). */
  async getOtherParticipantId(callId: string, userId: string): Promise<string | null> {
    const { rows } = await this.pool.query<{ caller_id: string; callee_id: string }>(
      `SELECT caller_id, callee_id FROM calls WHERE id = $1`,
      [callId],
    );
    const row = rows[0];
    if (!row) return null;
    if (row.caller_id === userId) return row.callee_id;
    if (row.callee_id === userId) return row.caller_id;
    return null;
  }

  async markAccepted(callId: string): Promise<CallRow | null> {
    const client: PoolClient = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const { rows } = await client.query<CallRow>(
        `UPDATE calls SET status = 'active', answered_at = now()
         WHERE id = $1 AND status = 'ringing' RETURNING *`,
        [callId],
      );
      const call = rows[0];
      if (call) {
        await client.query(
          `UPDATE call_participants SET joined_at = now() WHERE call_id = $1 AND user_id = $2`,
          [callId, call.callee_id],
        );
      }
      await client.query('COMMIT');
      return call ?? null;
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async markDeclined(callId: string): Promise<CallRow | null> {
    const { rows } = await this.pool.query<CallRow>(
      `UPDATE calls SET status = 'declined', ended_at = now(), end_reason = 'declined'
       WHERE id = $1 AND status = 'ringing' RETURNING *`,
      [callId],
    );
    return rows[0] ?? null;
  }

  /**
   * Terminal transition from either 'ringing' or 'active' — the resulting
   * status ('missed' vs 'ended') is derived here from whether answered_at
   * was ever set, not passed in by the caller, so a client can't claim a
   * call was answered when the server never recorded that (spec section 4).
   * Idempotent: ending an already-ended call is a no-op (returns null),
   * so a duplicate hangup (e.g. both the WS relay path and a retried REST
   * call landing close together) can't double-write end_reason.
   */
  async markEnded(callId: string, reason: string | null): Promise<CallRow | null> {
    const { rows } = await this.pool.query<CallRow>(
      `UPDATE calls
       SET ended_at = now(),
           end_reason = $2,
           status = CASE WHEN answered_at IS NULL THEN 'missed' ELSE 'ended' END
       WHERE id = $1 AND status IN ('ringing', 'active')
       RETURNING *`,
      [callId, reason],
    );
    return rows[0] ?? null;
  }

  async listHistoryForUser(userId: string, before: Date | null, limit: number): Promise<CallHistoryRow[]> {
    const { rows } = await this.pool.query<CallHistoryRow>(
      `SELECT c.*,
              other.id AS other_user_id,
              other.phone_e164 AS other_phone_e164,
              other.display_name AS other_display_name
       FROM calls c
       JOIN users other ON other.id = (CASE WHEN c.caller_id = $1 THEN c.callee_id ELSE c.caller_id END)
       WHERE (c.caller_id = $1 OR c.callee_id = $1)
         AND ($2::timestamptz IS NULL OR c.started_at < $2)
       ORDER BY c.started_at DESC
       LIMIT $3`,
      [userId, before, limit],
    );
    return rows;
  }
}
