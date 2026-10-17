import { BadRequestException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

const FILE_KINDS = new Set(['image', 'file', 'audio', 'video']);

/**
 * Personal cloud ("Saved Messages") and the Files browser. Saved items REFERENCE the existing
 * attachment (no second copy, no parallel storage subsystem) and remember where they came from.
 */
@Injectable()
export class CloudService {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  private kindOf(messageKind: string, body: string | null, hasAttachment: boolean): string {
    if (hasAttachment && FILE_KINDS.has(messageKind)) return messageKind;
    if (hasAttachment) return 'file';
    return body && /https?:\/\/\S+/i.test(body) ? 'link' : 'text';
  }

  async saveMessage(userId: string, messageId: string, folder?: string) {
    const { rows } = await this.pool.query(
      `SELECT m.id, m.conversation_id, m.kind, m.body, m.attachment_id, m.created_at,
              coalesce(u.display_name, u.username, 'Unknown') AS sender_name
         FROM messages m
         JOIN conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = $2
         JOIN users u ON u.id = m.sender_id
        WHERE m.id = $1 AND m.deleted_at IS NULL`,
      [messageId, userId],
    );
    const m = rows[0];
    if (!m) throw new NotFoundException('Message not found');
    const kind = this.kindOf(m.kind, m.body, !!m.attachment_id);
    try {
      const ins = await this.pool.query<{ id: string }>(
        `INSERT INTO saved_items (user_id, kind, body, attachment_id, source_conversation_id, source_message_id, source_sender_name, source_created_at, folder)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id`,
        [
          userId,
          kind,
          m.body,
          m.attachment_id,
          m.conversation_id,
          m.id,
          m.sender_name,
          m.created_at,
          folder?.slice(0, 40) ?? null,
        ],
      );
      return { id: ins.rows[0].id };
    } catch (e: any) {
      if (e?.code === '23505') return { id: 'already-saved' };
      throw e;
    }
  }

  async addNote(userId: string, body: string, folder?: string, tags?: string[]) {
    const text = (body ?? '').trim();
    if (!text || text.length > 8000)
      throw new BadRequestException('Note must be 1-8000 characters');
    const { rows } = await this.pool.query<{ id: string }>(
      "INSERT INTO saved_items (user_id, kind, body, folder, tags) VALUES ($1,'note',$2,$3,$4) RETURNING id",
      [
        userId,
        text,
        folder?.slice(0, 40) ?? null,
        (tags ?? []).slice(0, 10).map((t) => t.slice(0, 30)),
      ],
    );
    return { id: rows[0].id };
  }

  async list(
    userId: string,
    q: { q?: string; folder?: string; tag?: string; kind?: string; limit: number; offset: number },
  ) {
    const { rows } = await this.pool.query(
      `SELECT s.id, s.kind, s.body, s.folder, s.tags, s.created_at, s.source_conversation_id, s.source_message_id,
              s.source_sender_name, s.source_created_at, s.attachment_id,
              a.original_filename, a.mime_type, a.size_bytes
         FROM saved_items s LEFT JOIN attachments a ON a.id = s.attachment_id
        WHERE s.user_id = $1
          AND ($2::text IS NULL OR s.body ILIKE '%' || $2 || '%' OR a.original_filename ILIKE '%' || $2 || '%' OR s.source_sender_name ILIKE '%' || $2 || '%')
          AND ($3::text IS NULL OR s.folder = $3)
          AND ($4::text IS NULL OR $4 = ANY (s.tags))
          AND ($5::text IS NULL OR s.kind = $5)
        ORDER BY s.created_at DESC LIMIT $6 OFFSET $7`,
      [
        userId,
        q.q?.trim() || null,
        q.folder ?? null,
        q.tag ?? null,
        q.kind ?? null,
        q.limit,
        q.offset,
      ],
    );
    return rows.map((r) => ({
      id: r.id,
      kind: r.kind,
      text: r.body,
      folder: r.folder,
      tags: r.tags,
      savedAt: r.created_at,
      source: r.source_message_id
        ? {
            conversationId: r.source_conversation_id,
            messageId: r.source_message_id,
            senderName: r.source_sender_name,
            sentAt: r.source_created_at,
          }
        : null,
      attachment: r.attachment_id
        ? {
            id: r.attachment_id,
            filename: r.original_filename,
            mime: r.mime_type,
            sizeBytes: Number(r.size_bytes),
          }
        : null,
    }));
  }

  async update(userId: string, id: string, patch: { folder?: string | null; tags?: string[] }) {
    const { rowCount } = await this.pool.query(
      'UPDATE saved_items SET folder = $3, tags = $4 WHERE id = $1 AND user_id = $2',
      [
        id,
        userId,
        patch.folder?.slice(0, 40) ?? null,
        (patch.tags ?? []).slice(0, 10).map((t) => t.slice(0, 30)),
      ],
    );
    if (!rowCount) throw new NotFoundException();
  }

  async remove(userId: string, id: string) {
    await this.pool.query('DELETE FROM saved_items WHERE id = $1 AND user_id = $2', [id, userId]);
  }

  async folders(userId: string) {
    const f = await this.pool.query(
      'SELECT folder, count(*)::int AS n FROM saved_items WHERE user_id = $1 AND folder IS NOT NULL GROUP BY folder ORDER BY folder',
      [userId],
    );
    const t = await this.pool.query(
      'SELECT tag, count(*)::int AS n FROM saved_items, unnest(tags) AS tag WHERE user_id = $1 GROUP BY tag ORDER BY n DESC LIMIT 30',
      [userId],
    );
    return { folders: f.rows, tags: t.rows };
  }

  /** Every attachment in every conversation this account belongs to, filterable and sortable. */
  async files(
    userId: string,
    q: {
      kind?: string;
      conversationId?: string;
      senderId?: string;
      q?: string;
      sort: 'date' | 'size' | 'name';
      dir: 'asc' | 'desc';
      limit: number;
      offset: number;
    },
  ) {
    const sortCol =
      q.sort === 'size'
        ? 'a.size_bytes'
        : q.sort === 'name'
          ? 'a.original_filename'
          : 'a.created_at';
    const dir = q.dir === 'asc' ? 'ASC' : 'DESC';
    const { rows } = await this.pool.query(
      `SELECT a.id, a.kind, a.original_filename, a.mime_type, a.size_bytes, a.created_at, a.conversation_id,
              m.id AS message_id, m.sender_id, coalesce(u.display_name, u.username, '') AS sender_name
         FROM attachments a
         JOIN conversation_members cm ON cm.conversation_id = a.conversation_id AND cm.user_id = $1
         LEFT JOIN messages m ON m.attachment_id = a.id AND m.deleted_at IS NULL
         LEFT JOIN users u ON u.id = a.uploader_id
        WHERE ($2::text IS NULL OR a.kind = $2)
          AND ($3::uuid IS NULL OR a.conversation_id = $3)
          AND ($4::uuid IS NULL OR a.uploader_id = $4)
          AND ($5::text IS NULL OR a.original_filename ILIKE '%' || $5 || '%')
        ORDER BY ${sortCol} ${dir} LIMIT $6 OFFSET $7`,
      [
        userId,
        q.kind ?? null,
        q.conversationId ?? null,
        q.senderId ?? null,
        q.q?.trim() || null,
        q.limit,
        q.offset,
      ],
    );
    return rows.map((r) => ({
      id: r.id,
      kind: r.kind,
      filename: r.original_filename,
      mime: r.mime_type,
      sizeBytes: Number(r.size_bytes),
      createdAt: r.created_at,
      conversationId: r.conversation_id,
      messageId: r.message_id,
      senderId: r.sender_id,
      senderName: r.sender_name,
    }));
  }

  async usage(userId: string) {
    const files = await this.pool.query<{ kind: string; n: string; bytes: string }>(
      `SELECT a.kind, count(*) AS n, coalesce(sum(a.size_bytes),0) AS bytes FROM attachments a
         JOIN conversation_members cm ON cm.conversation_id = a.conversation_id AND cm.user_id = $1 GROUP BY a.kind`,
      [userId],
    );
    const saved = await this.pool.query<{ n: string; bytes: string }>(
      `SELECT count(*) AS n, coalesce(sum(a.size_bytes),0) AS bytes FROM saved_items s LEFT JOIN attachments a ON a.id = s.attachment_id WHERE s.user_id = $1`,
      [userId],
    );
    return {
      byKind: files.rows.map((r) => ({ kind: r.kind, count: Number(r.n), bytes: Number(r.bytes) })),
      saved: { count: Number(saved.rows[0].n), bytes: Number(saved.rows[0].bytes) },
    };
  }
}
