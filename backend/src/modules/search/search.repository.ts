import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface MessageSearchRow {
  id: string;
  conversation_id: string;
  sender_id: string;
  kind: string;
  body: string | null;
  sequence: string;
  created_at: Date;
  // Joined in for display context — which chat a hit came from — without
  // a second round trip per result.
  conversation_kind: string;
  conversation_title: string | null;
  other_user_id: string | null;
  other_user_display_name: string | null;
}

export interface ConversationSearchRow {
  id: string;
  kind: string;
  title: string | null;
  last_message_at: Date | null;
  other_user_id: string | null;
  other_user_phone_e164: string | null;
  other_user_display_name: string | null;
}

export interface FileSearchRow {
  id: string;
  conversation_id: string;
  uploader_id: string;
  kind: string;
  original_filename: string;
  mime_type: string;
  size_bytes: string;
  width_px: number | null;
  height_px: number | null;
  duration_ms: number | null;
  thumbnail_storage_key: string | null;
  created_at: Date;
}

/**
 * Every query here joins conversation_members on the requesting user's id
 * itself — search results are filtered to the caller's own conversations
 * inside the SQL, never by trusting a conversationId the client happened
 * to pass in (spec section 4/34: authorization is enforced server-side).
 * A caller-supplied conversationId (searchWithinConversation) only narrows
 * an already-authorized set further, via the same join.
 */
@Injectable()
export class SearchRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async searchMessages(
    userId: string,
    query: string,
    limit: number,
    conversationId: string | null,
  ): Promise<MessageSearchRow[]> {
    const { rows } = await this.pool.query<MessageSearchRow>(
      `SELECT
         m.id, m.conversation_id, m.sender_id, m.kind, m.body, m.sequence, m.created_at,
         c.kind AS conversation_kind,
         c.title AS conversation_title,
         other.id AS other_user_id,
         other.display_name AS other_user_display_name
       FROM messages m
       JOIN conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = $1
       JOIN conversations c ON c.id = m.conversation_id
       LEFT JOIN conversation_members other_cm
         ON other_cm.conversation_id = c.id AND other_cm.user_id != $1 AND c.kind = 'direct'
       LEFT JOIN users other ON other.id = other_cm.user_id
       WHERE m.deleted_at IS NULL
         AND m.body ILIKE '%' || $2 || '%'
         AND ($3::uuid IS NULL OR m.conversation_id = $3)
       ORDER BY m.created_at DESC
       LIMIT $4`,
      [userId, query, conversationId, limit],
    );
    return rows;
  }

  /**
   * Matches a group's title OR (for a direct conversation) the other
   * member's display name / username / phone number — a direct
   * conversation has no title of its own (see conversations.title's
   * comment in 007_phase5_group_conversations.sql), so its "chat search"
   * identity is entirely the other person.
   */
  async searchConversations(userId: string, query: string, limit: number): Promise<ConversationSearchRow[]> {
    const { rows } = await this.pool.query<ConversationSearchRow>(
      `SELECT DISTINCT ON (c.id)
         c.id, c.kind, c.title, c.last_message_at,
         other.id AS other_user_id,
         other.phone_e164 AS other_user_phone_e164,
         other.display_name AS other_user_display_name
       FROM conversations c
       JOIN conversation_members cm ON cm.conversation_id = c.id AND cm.user_id = $1
       LEFT JOIN conversation_members other_cm
         ON other_cm.conversation_id = c.id AND other_cm.user_id != $1 AND c.kind = 'direct'
       LEFT JOIN users other ON other.id = other_cm.user_id
       WHERE
         (c.kind = 'group' AND c.title ILIKE '%' || $2 || '%')
         OR (c.kind = 'direct' AND (
              other.display_name ILIKE '%' || $2 || '%'
              OR other.username ILIKE '%' || $2 || '%'
              OR other.phone_e164 ILIKE '%' || $2 || '%'
         ))
       ORDER BY c.id, c.last_message_at DESC NULLS LAST
       LIMIT $3`,
      [userId, query, limit],
    );
    return rows;
  }

  /**
   * query may be an empty string — that's "browse all files" (spec
   * section 43's "Search: Media / Files" also covers just listing shared
   * files, not only text-filtering by name), so the ILIKE clause below
   * degenerates to '%%' which matches everything rather than being
   * special-cased.
   */
  async searchFiles(
    userId: string,
    query: string,
    limit: number,
    conversationId: string | null,
  ): Promise<FileSearchRow[]> {
    const { rows } = await this.pool.query<FileSearchRow>(
      `SELECT a.id, a.conversation_id, a.uploader_id, a.kind, a.original_filename,
              a.mime_type, a.size_bytes, a.width_px, a.height_px, a.duration_ms,
              a.thumbnail_storage_key, a.created_at
       FROM attachments a
       JOIN conversation_members cm ON cm.conversation_id = a.conversation_id AND cm.user_id = $1
       WHERE a.original_filename ILIKE '%' || $2 || '%'
         AND ($3::uuid IS NULL OR a.conversation_id = $3)
       ORDER BY a.created_at DESC
       LIMIT $4`,
      [userId, query, conversationId, limit],
    );
    return rows;
  }
}
