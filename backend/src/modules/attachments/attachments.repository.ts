import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface AttachmentRow {
  id: string;
  uploader_id: string;
  conversation_id: string;
  kind: string;
  storage_key: string;
  thumbnail_storage_key: string | null;
  original_filename: string;
  mime_type: string;
  size_bytes: string; // BIGINT comes back as a string from pg
  width_px: number | null;
  height_px: number | null;
  duration_ms: number | null;
  created_at: Date;
}

export interface CreateAttachmentInput {
  uploaderId: string;
  conversationId: string;
  kind: 'image' | 'file' | 'audio' | 'video';
  storageKey: string;
  thumbnailStorageKey: string | null;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  widthPx: number | null;
  heightPx: number | null;
  durationMs: number | null;
}

@Injectable()
export class AttachmentsRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(input: CreateAttachmentInput): Promise<AttachmentRow> {
    const { rows } = await this.pool.query<AttachmentRow>(
      `INSERT INTO attachments
        (uploader_id, conversation_id, kind, storage_key, thumbnail_storage_key,
         original_filename, mime_type, size_bytes, width_px, height_px, duration_ms)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
       RETURNING *`,
      [
        input.uploaderId,
        input.conversationId,
        input.kind,
        input.storageKey,
        input.thumbnailStorageKey,
        input.originalFilename,
        input.mimeType,
        input.sizeBytes,
        input.widthPx,
        input.heightPx,
        input.durationMs,
      ],
    );
    return rows[0];
  }

  async findById(id: string): Promise<AttachmentRow | null> {
    const { rows } = await this.pool.query<AttachmentRow>(
      `SELECT * FROM attachments WHERE id = $1`,
      [id],
    );
    return rows[0] ?? null;
  }
}
