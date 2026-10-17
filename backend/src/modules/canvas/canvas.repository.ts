import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';
import { Stroke } from './strokes';

@Injectable()
export class CanvasRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async get(conversationId: string): Promise<Stroke[]> {
    const { rows } = await this.pool.query<{ strokes: Stroke[] }>(
      'SELECT strokes FROM couple_canvas WHERE conversation_id = $1',
      [conversationId],
    );
    return rows[0]?.strokes ?? [];
  }

  /** Atomic append: two people drawing at once never overwrite each other. Returns the new stroke count. */
  async append(conversationId: string, strokes: Stroke[]): Promise<number> {
    const { rows } = await this.pool.query<{ n: number }>(
      `INSERT INTO couple_canvas (conversation_id, strokes) VALUES ($1, $2::jsonb)
       ON CONFLICT (conversation_id)
       DO UPDATE SET strokes = couple_canvas.strokes || EXCLUDED.strokes, updated_at = now()
       RETURNING jsonb_array_length(strokes) AS n`,
      [conversationId, JSON.stringify(strokes)],
    );
    return rows[0].n;
  }

  async clear(conversationId: string): Promise<void> {
    await this.pool.query(
      `INSERT INTO couple_canvas (conversation_id, strokes) VALUES ($1, '[]'::jsonb)
       ON CONFLICT (conversation_id) DO UPDATE SET strokes = '[]'::jsonb, updated_at = now()`,
      [conversationId],
    );
  }
}
