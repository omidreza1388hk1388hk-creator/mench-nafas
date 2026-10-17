import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface StoryRow {
  id: string;
  conversation_id: string;
  author_id: string;
  body: string;
  emoji: string;
  palette: number;
  attachment_id: string | null;
  created_at: Date;
  expires_at: Date;
  seen_by_viewer: boolean;
  views: number;
}

@Injectable()
export class StoriesRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  /** Active (not expired) stories, oldest first, with whether the viewer saw each and how many others saw it. */
  async listActive(conversationId: string, viewerId: string): Promise<StoryRow[]> {
    const { rows } = await this.pool.query<StoryRow>(
      `SELECT s.*,
              EXISTS (SELECT 1 FROM conversation_story_views v WHERE v.story_id = s.id AND v.user_id = $2) AS seen_by_viewer,
              (SELECT COUNT(*)::int FROM conversation_story_views v WHERE v.story_id = s.id AND v.user_id <> s.author_id) AS views
         FROM conversation_stories s
        WHERE s.conversation_id = $1 AND s.expires_at > now()
        ORDER BY s.created_at ASC`,
      [conversationId, viewerId],
    );
    return rows;
  }

  /** Per conversation of the user: how many active stories by others they have not seen yet. */
  async unseenByConversation(userId: string): Promise<{ conversation_id: string; n: number }[]> {
    const { rows } = await this.pool.query<{ conversation_id: string; n: number }>(
      `SELECT s.conversation_id, COUNT(*)::int AS n
         FROM conversation_stories s
         JOIN conversation_members m ON m.conversation_id = s.conversation_id AND m.user_id = $1
        WHERE s.expires_at > now() AND s.author_id <> $1
          AND NOT EXISTS (SELECT 1 FROM conversation_story_views v WHERE v.story_id = s.id AND v.user_id = $1)
        GROUP BY s.conversation_id`,
      [userId],
    );
    return rows;
  }

  async countActiveByAuthor(conversationId: string, authorId: string): Promise<number> {
    const { rows } = await this.pool.query<{ n: string }>(
      `SELECT COUNT(*)::text AS n FROM conversation_stories
        WHERE conversation_id = $1 AND author_id = $2 AND expires_at > now()`,
      [conversationId, authorId],
    );
    return Number(rows[0].n);
  }

  async create(
    conversationId: string,
    authorId: string,
    body: string,
    emoji: string,
    palette: number,
    attachmentId: string | null = null,
  ): Promise<StoryRow> {
    const { rows } = await this.pool.query<StoryRow>(
      `INSERT INTO conversation_stories (conversation_id, author_id, body, emoji, palette, attachment_id)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING *, false AS seen_by_viewer, 0 AS views`,
      [conversationId, authorId, body, emoji, palette, attachmentId],
    );
    return rows[0];
  }

  async findById(
    storyId: string,
  ): Promise<{ id: string; conversation_id: string; author_id: string; expires_at: Date } | null> {
    const { rows } = await this.pool.query(
      'SELECT id, conversation_id, author_id, expires_at FROM conversation_stories WHERE id = $1',
      [storyId],
    );
    return rows[0] ?? null;
  }

  async markViewed(storyId: string, userId: string): Promise<void> {
    await this.pool.query(
      'INSERT INTO conversation_story_views (story_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING',
      [storyId, userId],
    );
  }

  async delete(storyId: string): Promise<void> {
    await this.pool.query('DELETE FROM conversation_stories WHERE id = $1', [storyId]);
  }
}
