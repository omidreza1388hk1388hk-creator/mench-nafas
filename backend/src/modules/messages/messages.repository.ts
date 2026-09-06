import { Inject, Injectable } from '@nestjs/common';
import { Pool, PoolClient } from 'pg';
import { PG_POOL } from '../../database/database.module';
import { ReactionSummary } from '../realtime/realtime-events';

export interface MessageRow {
  id: string;
  conversation_id: string;
  sender_id: string;
  client_msg_id: string;
  kind: string;
  body: string | null;
  attachment_id: string | null;
  sequence: string; // BIGSERIAL comes back as a string from pg to avoid precision loss
  created_at: Date;
  edited_at: Date | null;
  deleted_at: Date | null;
  forwarded_from_message_id: string | null;
  // Populated only by queries that JOIN attachments (both methods below
  // do) — null/undefined whenever attachment_id itself is null.
  attachment_kind: string | null;
  attachment_mime_type: string | null;
  attachment_original_filename: string | null;
  attachment_size_bytes: string | null;
  attachment_width_px: number | null;
  attachment_height_px: number | null;
  attachment_duration_ms: number | null;
  attachment_thumbnail_storage_key: string | null;
}

const MESSAGE_SELECT_WITH_ATTACHMENT = `
  SELECT
    m.*,
    a.kind AS attachment_kind,
    a.mime_type AS attachment_mime_type,
    a.original_filename AS attachment_original_filename,
    a.size_bytes AS attachment_size_bytes,
    a.width_px AS attachment_width_px,
    a.height_px AS attachment_height_px,
    a.duration_ms AS attachment_duration_ms,
    a.thumbnail_storage_key AS attachment_thumbnail_storage_key
  FROM messages m
  LEFT JOIN attachments a ON a.id = m.attachment_id
`;

export interface ReadReceiptRow {
  conversation_id: string;
  user_id: string;
  last_read_sequence: string;
  updated_at: Date;
}

export interface CreateMessageInput {
  conversationId: string;
  senderId: string;
  clientMsgId: string;
  kind: 'text' | 'image' | 'file' | 'audio' | 'video';
  body: string | null;
  attachmentId: string | null;
  forwardedFromMessageId?: string | null;
}

@Injectable()
export class MessagesRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  /**
   * Idempotent send: (conversation_id, sender_id, client_msg_id) is
   * uniquely indexed (see migration 001), so retrying the same send
   * (e.g. Android's outbox retrying after a dropped response) returns the
   * original row instead of creating a duplicate message. Runs in an
   * explicit transaction because it also updates the parent conversation's
   * last_message_at, and both writes should succeed or fail together.
   */
  async createIdempotent(input: CreateMessageInput): Promise<MessageRow> {
    const client: PoolClient = await this.pool.connect();
    try {
      await client.query('BEGIN');

      const { rows } = await client.query<{ id: string; created_at: Date }>(
        `INSERT INTO messages (conversation_id, sender_id, client_msg_id, kind, body, attachment_id, forwarded_from_message_id)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         ON CONFLICT (conversation_id, sender_id, client_msg_id)
         DO UPDATE SET conversation_id = EXCLUDED.conversation_id
         RETURNING id, created_at`,
        [
          input.conversationId,
          input.senderId,
          input.clientMsgId,
          input.kind,
          input.body,
          input.attachmentId,
          input.forwardedFromMessageId ?? null,
        ],
      );
      const inserted = rows[0];

      await client.query(
        `UPDATE conversations SET last_message_at = $2 WHERE id = $1`,
        [input.conversationId, inserted.created_at],
      );

      // Re-fetched (rather than built from the INSERT...RETURNING result
      // directly) so the response includes the joined attachment summary
      // — the plain INSERT above can't JOIN, and the client needs that
      // summary in the same response it gets back from sending, not just
      // on the next history fetch.
      const { rows: fullRows } = await client.query<MessageRow>(
        `${MESSAGE_SELECT_WITH_ATTACHMENT} WHERE m.id = $1`,
        [inserted.id],
      );

      await client.query('COMMIT');
      return fullRows[0];
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  /**
   * Deliberately does NOT filter out deleted_at IS NOT NULL rows (unlike
   * the pre-Phase-4 version of this query) — a deleted message stays a
   * placeholder ("This message was deleted") at its original position in
   * history, not a gap in the sequence. The client decides how to render
   * a row with deletedAt set; the server's job is just to not hide it.
   */
  async listForConversation(
    conversationId: string,
    afterSequence: number,
    limit: number,
  ): Promise<MessageRow[]> {
    const { rows } = await this.pool.query<MessageRow>(
      `${MESSAGE_SELECT_WITH_ATTACHMENT}
       WHERE m.conversation_id = $1 AND m.sequence > $2
       ORDER BY m.sequence ASC
       LIMIT $3`,
      [conversationId, afterSequence, limit],
    );
    return rows;
  }

  async findById(messageId: string): Promise<MessageRow | null> {
    const { rows } = await this.pool.query<MessageRow>(
      `${MESSAGE_SELECT_WITH_ATTACHMENT} WHERE m.id = $1`,
      [messageId],
    );
    return rows[0] ?? null;
  }

  /**
   * Ownership is enforced in the WHERE clause, not just checked
   * beforehand in the service layer — a row that doesn't match
   * (senderId, not already deleted) simply isn't updated, so the caller
   * gets back null and can distinguish "not found/not yours/already
   * deleted" from a real update without a separate SELECT-then-UPDATE
   * race.
   */
  async editBody(messageId: string, senderId: string, body: string): Promise<MessageRow | null> {
    const { rows } = await this.pool.query<{ id: string }>(
      `UPDATE messages SET body = $3, edited_at = now()
       WHERE id = $1 AND sender_id = $2 AND deleted_at IS NULL AND kind = 'text'
       RETURNING id`,
      [messageId, senderId, body],
    );
    if (rows.length === 0) return null;
    return this.findById(messageId);
  }

  async softDelete(messageId: string, senderId: string): Promise<MessageRow | null> {
    const { rows } = await this.pool.query<{ id: string }>(
      `UPDATE messages SET deleted_at = now()
       WHERE id = $1 AND sender_id = $2 AND deleted_at IS NULL
       RETURNING id`,
      [messageId, senderId],
    );
    if (rows.length === 0) return null;
    return this.findById(messageId);
  }

  /**
   * Upsert-by-replace: a user can only have one active reaction per
   * message (see 006_phase4_message_actions.sql's PK), so picking a new
   * emoji overwrites their previous one on this message rather than
   * stacking a second row.
   */
  async setReaction(messageId: string, userId: string, emoji: string): Promise<void> {
    await this.pool.query(
      `INSERT INTO message_reactions (message_id, user_id, emoji)
       VALUES ($1, $2, $3)
       ON CONFLICT (message_id, user_id) DO UPDATE SET emoji = EXCLUDED.emoji, created_at = now()`,
      [messageId, userId, emoji],
    );
  }

  async clearReaction(messageId: string, userId: string): Promise<void> {
    await this.pool.query(
      `DELETE FROM message_reactions WHERE message_id = $1 AND user_id = $2`,
      [messageId, userId],
    );
  }

  async listReactionsForMessage(messageId: string): Promise<ReactionSummary[]> {
    const { rows } = await this.pool.query<{ emoji: string; user_ids: string[] }>(
      `SELECT emoji, array_agg(user_id ORDER BY created_at) AS user_ids
       FROM message_reactions WHERE message_id = $1
       GROUP BY emoji`,
      [messageId],
    );
    return rows.map((r) => ({ emoji: r.emoji, userIds: r.user_ids }));
  }

  /**
   * Batched, not one query per message — listForConversation returns up
   * to `limit` rows, and fetching reactions one-by-one for each would be
   * exactly the N+1 pattern the attachment JOIN above was written to
   * avoid. GROUP BY here nests both the emoji-level and message-level
   * aggregation into a single round trip.
   */
  async listReactionsForMessages(messageIds: string[]): Promise<Map<string, ReactionSummary[]>> {
    const map = new Map<string, ReactionSummary[]>();
    if (messageIds.length === 0) return map;

    const { rows } = await this.pool.query<{ message_id: string; emoji: string; user_ids: string[] }>(
      `SELECT message_id, emoji, array_agg(user_id ORDER BY created_at) AS user_ids
       FROM message_reactions WHERE message_id = ANY($1::uuid[])
       GROUP BY message_id, emoji`,
      [messageIds],
    );
    for (const row of rows) {
      const list = map.get(row.message_id) ?? [];
      list.push({ emoji: row.emoji, userIds: row.user_ids });
      map.set(row.message_id, list);
    }
    return map;
  }

  async upsertReadReceipt(
    conversationId: string,
    userId: string,
    lastReadSequence: number,
  ): Promise<ReadReceiptRow> {
    const { rows } = await this.pool.query<ReadReceiptRow>(
      `INSERT INTO read_receipts (conversation_id, user_id, last_read_sequence)
       VALUES ($1, $2, $3)
       ON CONFLICT (conversation_id, user_id)
       DO UPDATE SET
         last_read_sequence = GREATEST(read_receipts.last_read_sequence, EXCLUDED.last_read_sequence),
         updated_at = now()
       RETURNING *`,
      [conversationId, userId, lastReadSequence],
    );
    return rows[0];
  }
}
