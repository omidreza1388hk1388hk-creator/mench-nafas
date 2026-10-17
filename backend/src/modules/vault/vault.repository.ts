import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface VaultRow {
  id: string;
  conversation_id: string;
  author_id: string;
  blob: string;
  created_at: Date;
}

@Injectable()
export class VaultRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async list(conversationId: string): Promise<VaultRow[]> {
    const { rows } = await this.pool.query<VaultRow>(
      'SELECT * FROM couple_vault_items WHERE conversation_id = $1 ORDER BY created_at ASC',
      [conversationId],
    );
    return rows;
  }

  async count(conversationId: string): Promise<number> {
    const { rows } = await this.pool.query<{ n: string }>(
      'SELECT COUNT(*)::text AS n FROM couple_vault_items WHERE conversation_id = $1',
      [conversationId],
    );
    return Number(rows[0].n);
  }

  async create(conversationId: string, authorId: string, blob: string): Promise<VaultRow> {
    const { rows } = await this.pool.query<VaultRow>(
      'INSERT INTO couple_vault_items (conversation_id, author_id, blob) VALUES ($1, $2, $3) RETURNING *',
      [conversationId, authorId, blob],
    );
    return rows[0];
  }

  async findById(itemId: string): Promise<VaultRow | null> {
    const { rows } = await this.pool.query<VaultRow>('SELECT * FROM couple_vault_items WHERE id = $1', [itemId]);
    return rows[0] ?? null;
  }

  async delete(itemId: string): Promise<void> {
    await this.pool.query('DELETE FROM couple_vault_items WHERE id = $1', [itemId]);
  }
}
