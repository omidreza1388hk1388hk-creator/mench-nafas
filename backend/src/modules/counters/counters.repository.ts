import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface CounterRow {
  id: string;
  conversation_id: string;
  author_id: string;
  title: string;
  emoji: string;
  kind: 'date' | 'tally';
  anchor_date: string | null;
  tally_count: number;
  theme: number;
  created_at: Date;
}

@Injectable()
export class CountersRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async list(conversationId: string): Promise<CounterRow[]> {
    const { rows } = await this.pool.query<CounterRow>(
      'SELECT * FROM couple_counters WHERE conversation_id = $1 ORDER BY created_at ASC',
      [conversationId],
    );
    return rows;
  }

  async count(conversationId: string): Promise<number> {
    const { rows } = await this.pool.query<{ n: string }>(
      'SELECT COUNT(*)::text AS n FROM couple_counters WHERE conversation_id = $1',
      [conversationId],
    );
    return Number(rows[0].n);
  }

  async create(
    conversationId: string,
    authorId: string,
    input: { title: string; emoji: string; kind: 'date' | 'tally'; anchorDate: string | null; theme: number },
  ): Promise<CounterRow> {
    const { rows } = await this.pool.query<CounterRow>(
      `INSERT INTO couple_counters (conversation_id, author_id, title, emoji, kind, anchor_date, theme)
       VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *`,
      [conversationId, authorId, input.title, input.emoji, input.kind, input.anchorDate, input.theme],
    );
    return rows[0];
  }

  async findById(counterId: string): Promise<CounterRow | null> {
    const { rows } = await this.pool.query<CounterRow>('SELECT * FROM couple_counters WHERE id = $1', [counterId]);
    return rows[0] ?? null;
  }

  /** Atomic, so two people tapping at once never lose a tap. Never goes below 0. */
  async bump(counterId: string, delta: 1 | -1): Promise<CounterRow | null> {
    const { rows } = await this.pool.query<CounterRow>(
      `UPDATE couple_counters SET tally_count = GREATEST(0, tally_count + $2)
       WHERE id = $1 AND kind = 'tally' RETURNING *`,
      [counterId, delta],
    );
    return rows[0] ?? null;
  }

  async delete(counterId: string): Promise<void> {
    await this.pool.query('DELETE FROM couple_counters WHERE id = $1', [counterId]);
  }
}
