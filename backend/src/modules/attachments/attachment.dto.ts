import { AttachmentRow } from './attachments.repository';

export interface AttachmentDto {
  id: string;
  conversationId: string;
  uploaderId: string;
  kind: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  widthPx: number | null;
  heightPx: number | null;
  durationMs: number | null;
  hasThumbnail: boolean;
  createdAt: string;
}

export function toAttachmentDto(row: AttachmentRow): AttachmentDto {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    uploaderId: row.uploader_id,
    kind: row.kind,
    originalFilename: row.original_filename,
    mimeType: row.mime_type,
    // BIGINT comes back from pg as a string — see MessageDto's sequence
    // field in realtime-events.ts for the same reasoning: attachment
    // sizes here are nowhere near Number.MAX_SAFE_INTEGER (2^53 bytes).
    sizeBytes: Number(row.size_bytes),
    widthPx: row.width_px,
    heightPx: row.height_px,
    durationMs: row.duration_ms,
    hasThumbnail: row.thumbnail_storage_key !== null,
    createdAt: row.created_at.toISOString(),
  };
}
