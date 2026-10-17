import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface AlbumRow {
  id: string;
  conversation_id: string;
  author_id: string;
  attachment_id: string;
  caption: string;
  created_at: Date;
  /** The viewer's own reaction, if any. */
  my_reaction: string | null;
  /** The other person's reaction, if any. */
  other_reaction: string | null;
}

@Injectable()
export class AlbumRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async list(conversationId: string, viewerId: string): Promise<AlbumRow[]> {
    const { rows } = await this.pool.query<AlbumRow>(
      `SELECT a.*,
              (SELECT r.emoji FROM album_reactions r WHERE r.item_id = a.id AND r.user_id = $2) AS my_reaction,
              (SELECT r.emoji FROM album_reactions r WHERE r.item_id = a.id AND r.user_id <> $2 LIMIT 1) AS other_reaction
         FROM album_items a WHERE a.conversation_id = $1 ORDER BY a.created_at DESC`,
      [conversationId, viewerId],
    );
    return rows;
  }

  async count(conversationId: string): Promise<number> {
    const { rows } = await this.pool.query<{ n: string }>(
      'SELECT COUNT(*)::text AS n FROM album_items WHERE conversation_id = $1',
      [conversationId],
    );
    return Number(rows[0].n);
  }

  async create(
    conversationId: string,
    authorId: string,
    attachmentId: string,
    caption: string,
  ): Promise<AlbumRow> {
    const { rows } = await this.pool.query<AlbumRow>(
      `INSERT INTO album_items (conversation_id, author_id, attachment_id, caption) VALUES ($1, $2, $3, $4)
       RETURNING *, NULL::text AS my_reaction, NULL::text AS other_reaction`,
      [conversationId, authorId, attachmentId, caption],
    );
    return rows[0];
  }

  async findById(
    itemId: string,
  ): Promise<{ id: string; conversation_id: string; author_id: string } | null> {
    const { rows } = await this.pool.query(
      'SELECT id, conversation_id, author_id FROM album_items WHERE id = $1',
      [itemId],
    );
    return rows[0] ?? null;
  }

  async setReaction(itemId: string, userId: string, emoji: string | null): Promise<void> {
    if (emoji === null) {
      await this.pool.query('DELETE FROM album_reactions WHERE item_id = $1 AND user_id = $2', [
        itemId,
        userId,
      ]);
    } else {
      await this.pool.query(
        `INSERT INTO album_reactions (item_id, user_id, emoji) VALUES ($1, $2, $3)
         ON CONFLICT (item_id, user_id) DO UPDATE SET emoji = EXCLUDED.emoji`,
        [itemId, userId, emoji],
      );
    }
  }

  async delete(itemId: string): Promise<void> {
    await this.pool.query('DELETE FROM album_items WHERE id = $1', [itemId]);
  }
}
