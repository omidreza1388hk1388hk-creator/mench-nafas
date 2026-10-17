import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface LetterRow {
  id: string;
  conversation_id: string;
  author_id: string;
  title: string;
  body: string;
  unlock_at: Date | null;
  attachment_id: string | null;
  opened_at: Date | null;
  created_at: Date;
}

@Injectable()
export class LettersRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(
    conversationId: string,
    authorId: string,
    title: string,
    body: string,
    unlockAt: string | null,
    attachmentId: string | null = null,
  ): Promise<LetterRow> {
    const { rows } = await this.pool.query<LetterRow>(
      `INSERT INTO love_letters (conversation_id, author_id, title, body, unlock_at, attachment_id)
       VALUES ($1, $2, $3, $4, $5, $6) RETURNING *`,
      [conversationId, authorId, title, body, unlockAt, attachmentId],
    );
    return rows[0];
  }

  async listForConversation(conversationId: string): Promise<LetterRow[]> {
    const { rows } = await this.pool.query<LetterRow>(
      `SELECT * FROM love_letters WHERE conversation_id = $1 ORDER BY created_at DESC`,
      [conversationId],
    );
    return rows;
  }

  /**
   * Atomically claims letters whose unlock time has passed and whose reminder has not been sent. Marking them in the same
   * statement (with SKIP LOCKED) means a letter is reminded exactly once even with several server instances.
   */
  async claimDueReminders(limit: number): Promise<{ id: string; conversation_id: string; author_id: string }[]> {
    const { rows } = await this.pool.query<{ id: string; conversation_id: string; author_id: string }>(
      `UPDATE love_letters SET unlock_notified_at = now()
        WHERE id IN (
          SELECT id FROM love_letters
           WHERE unlock_at IS NOT NULL AND unlock_at <= now() AND unlock_notified_at IS NULL AND opened_at IS NULL
           ORDER BY unlock_at ASC LIMIT $1 FOR UPDATE SKIP LOCKED
        )
        RETURNING id, conversation_id, author_id`,
      [limit],
    );
    return rows;
  }

  async findById(letterId: string): Promise<LetterRow | null> {
    const { rows } = await this.pool.query<LetterRow>('SELECT * FROM love_letters WHERE id = $1', [letterId]);
    return rows[0] ?? null;
  }

  /** Idempotent: only the first open stamps opened_at. Returns the (possibly already-opened) row. */
  async markOpened(letterId: string): Promise<LetterRow> {
    const { rows } = await this.pool.query<LetterRow>(
      `UPDATE love_letters SET opened_at = COALESCE(opened_at, now()) WHERE id = $1 RETURNING *`,
      [letterId],
    );
    return rows[0];
  }
}
