import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface WishRow {
  id: string;
  conversation_id: string;
  author_id: string;
  title: string;
  done: boolean;
  done_at: Date | null;
  created_at: Date;
}

@Injectable()
export class CoupleRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async upsertAnswer(conversationId: string, date: string, userId: string, answer: string): Promise<void> {
    await this.pool.query(
      `INSERT INTO daily_answers (conversation_id, question_date, user_id, answer)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT (conversation_id, question_date, user_id)
       DO UPDATE SET answer = EXCLUDED.answer, answered_at = now()`,
      [conversationId, date, userId, answer],
    );
  }

  /** Days (YYYY-MM-DD) on which both people answered, newest first. */
  async listBothAnsweredDates(conversationId: string): Promise<string[]> {
    const { rows } = await this.pool.query<{ question_date: string }>(
      `SELECT question_date FROM daily_answers WHERE conversation_id = $1
       GROUP BY question_date HAVING COUNT(DISTINCT user_id) >= 2
       ORDER BY question_date DESC LIMIT 800`,
      [conversationId],
    );
    return rows.map((r: { question_date: string }) => r.question_date);
  }

  async listAnswers(conversationId: string, date: string): Promise<{ user_id: string; answer: string }[]> {
    const { rows } = await this.pool.query(
      'SELECT user_id, answer FROM daily_answers WHERE conversation_id = $1 AND question_date = $2',
      [conversationId, date],
    );
    return rows;
  }

  async listWishes(conversationId: string): Promise<WishRow[]> {
    const { rows } = await this.pool.query<WishRow>(
      'SELECT * FROM wishlist_items WHERE conversation_id = $1 ORDER BY done ASC, created_at DESC',
      [conversationId],
    );
    return rows;
  }

  async createWish(conversationId: string, authorId: string, title: string): Promise<WishRow> {
    const { rows } = await this.pool.query<WishRow>(
      'INSERT INTO wishlist_items (conversation_id, author_id, title) VALUES ($1, $2, $3) RETURNING *',
      [conversationId, authorId, title],
    );
    return rows[0];
  }

  async findWish(wishId: string): Promise<WishRow | null> {
    const { rows } = await this.pool.query<WishRow>('SELECT * FROM wishlist_items WHERE id = $1', [wishId]);
    return rows[0] ?? null;
  }

  async setWishDone(wishId: string, done: boolean): Promise<WishRow> {
    const { rows } = await this.pool.query<WishRow>(
      'UPDATE wishlist_items SET done = $2, done_at = CASE WHEN $2 THEN now() ELSE NULL END WHERE id = $1 RETURNING *',
      [wishId, done],
    );
    return rows[0];
  }

  async deleteWish(wishId: string): Promise<void> {
    await this.pool.query('DELETE FROM wishlist_items WHERE id = $1', [wishId]);
  }
}
