import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';
import { ScheduledMessageDto } from './scheduled-message.dto';

export interface ScheduledRow {
  id: string;
  conversation_id: string;
  sender_id: string;
  client_msg_id: string;
  body: string;
  silent: boolean;
  send_at: Date;
  status: 'pending' | 'sending' | 'sent' | 'failed';
  created_at: Date;
}

export function toScheduledDto(row: ScheduledRow): ScheduledMessageDto {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    body: row.body,
    silent: row.silent,
    sendAt: row.send_at.toISOString(),
    status: row.status,
    createdAt: row.created_at.toISOString(),
  };
}

@Injectable()
export class ScheduledMessagesRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async countPending(conversationId: string, senderId: string): Promise<number> {
    const { rows } = await this.pool.query<{ n: string }>(
      `SELECT COUNT(*) AS n FROM scheduled_messages WHERE conversation_id = $1 AND sender_id = $2 AND status = 'pending'`,
      [conversationId, senderId],
    );
    return Number(rows[0].n);
  }

  /** Idempotent on (conversation, sender, client_msg_id): a retried request returns the row that already exists. */
  async create(input: { conversationId: string; senderId: string; clientMsgId: string; body: string; silent: boolean; sendAt: Date }): Promise<ScheduledRow> {
    const { rows } = await this.pool.query<ScheduledRow>(
      `INSERT INTO scheduled_messages (conversation_id, sender_id, client_msg_id, body, silent, send_at)
       VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (conversation_id, sender_id, client_msg_id) DO UPDATE SET client_msg_id = EXCLUDED.client_msg_id
       RETURNING *`,
      [input.conversationId, input.senderId, input.clientMsgId, input.body, input.silent, input.sendAt],
    );
    return rows[0];
  }

  async listPending(conversationId: string, senderId: string): Promise<ScheduledRow[]> {
    const { rows } = await this.pool.query<ScheduledRow>(
      `SELECT * FROM scheduled_messages WHERE conversation_id = $1 AND sender_id = $2 AND status = 'pending' ORDER BY send_at`,
      [conversationId, senderId],
    );
    return rows;
  }

  /** Deletes a pending message of this sender; false if it does not exist, is not theirs or already went out. */
  async deletePending(id: string, senderId: string): Promise<boolean> {
    const result = await this.pool.query(`DELETE FROM scheduled_messages WHERE id = $1 AND sender_id = $2 AND status = 'pending'`, [id, senderId]);
    return (result.rowCount ?? 0) > 0;
  }

  /** Atomically takes up to [limit] due messages (other workers skip them), marking them 'sending'. */
  async claimDue(limit: number): Promise<ScheduledRow[]> {
    const { rows } = await this.pool.query<ScheduledRow>(
      `UPDATE scheduled_messages SET status = 'sending'
       WHERE id IN (
         SELECT id FROM scheduled_messages WHERE status = 'pending' AND send_at <= now()
         ORDER BY send_at LIMIT $1 FOR UPDATE SKIP LOCKED
       ) RETURNING *`,
      [limit],
    );
    return rows;
  }

  async markSent(id: string): Promise<void> {
    await this.pool.query(`UPDATE scheduled_messages SET status = 'sent', sent_at = now(), error = NULL WHERE id = $1`, [id]);
  }

  async markFailed(id: string, error: string): Promise<void> {
    await this.pool.query(`UPDATE scheduled_messages SET status = 'failed', error = $2 WHERE id = $1`, [id, error.slice(0, 300)]);
  }

  /** A crash between claim and send leaves rows in 'sending'; hand them back so the next tick retries (send is idempotent). */
  async releaseStuck(olderThanSeconds: number): Promise<void> {
    await this.pool.query(
      `UPDATE scheduled_messages SET status = 'pending' WHERE status = 'sending' AND send_at < now() - ($1 || ' seconds')::interval`,
      [String(olderThanSeconds)],
    );
  }
}
