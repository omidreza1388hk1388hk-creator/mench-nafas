import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface MemoryRow {
  id: string;
  conversation_id: string;
  author_id: string;
  title: string;
  note: string | null;
  memory_date: string;
  attachment_id: string | null;
  created_at: Date;
}

@Injectable()
export class MemoriesRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(
    conversationId: string,
    authorId: string,
    input: { title: string; note: string | null; memoryDate: string; attachmentId: string | null },
  ): Promise<MemoryRow> {
    const { rows } = await this.pool.query<MemoryRow>(
      `INSERT INTO memories (conversation_id, author_id, title, note, memory_date, attachment_id)
       VALUES ($1, $2, $3, $4, $5, $6) RETURNING *`,
      [conversationId, authorId, input.title, input.note, input.memoryDate, input.attachmentId],
    );
    return rows[0];
  }

  async list(conversationId: string): Promise<MemoryRow[]> {
    const { rows } = await this.pool.query<MemoryRow>(
      `SELECT * FROM memories WHERE conversation_id = $1 ORDER BY memory_date DESC, created_at DESC`,
      [conversationId],
    );
    return rows;
  }

  async findById(memoryId: string): Promise<MemoryRow | null> {
    const { rows } = await this.pool.query<MemoryRow>('SELECT * FROM memories WHERE id = $1', [
      memoryId,
    ]);
    return rows[0] ?? null;
  }

  async delete(memoryId: string): Promise<void> {
    await this.pool.query('DELETE FROM memories WHERE id = $1', [memoryId]);
  }

  /** The photo must be an image uploaded to THIS conversation (queried directly to avoid a module cycle with AttachmentsModule). */
  async isImageAttachmentOfConversation(
    attachmentId: string,
    conversationId: string,
  ): Promise<boolean> {
    const { rows } = await this.pool.query(
      `SELECT 1 FROM attachments WHERE id = $1 AND conversation_id = $2 AND kind = 'image'`,
      [attachmentId, conversationId],
    );
    return rows.length > 0;
  }
}
