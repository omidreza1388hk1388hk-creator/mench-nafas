import { MessageSearchRow, ConversationSearchRow, FileSearchRow } from './search.repository';

export interface MessageSearchResultDto {
  id: string;
  conversationId: string;
  senderId: string;
  kind: string;
  body: string | null;
  sequence: number;
  createdAt: string;
  // Display context so a tapped result can jump straight into the right
  // chat with a sensible title, without the client issuing a follow-up
  // "get conversation" call first.
  conversationTitle: string | null;
  conversationOtherUserDisplayName: string | null;
}

export function toMessageSearchResultDto(row: MessageSearchRow): MessageSearchResultDto {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    senderId: row.sender_id,
    kind: row.kind,
    body: row.body,
    sequence: Number(row.sequence),
    createdAt: row.created_at.toISOString(),
    conversationTitle: row.conversation_title,
    conversationOtherUserDisplayName: row.other_user_display_name,
  };
}

export interface ConversationSearchResultDto {
  id: string;
  kind: string;
  title: string | null;
  lastMessageAt: string | null;
  otherUserId: string | null;
  otherUserPhoneE164: string | null;
  otherUserDisplayName: string | null;
}

export function toConversationSearchResultDto(row: ConversationSearchRow): ConversationSearchResultDto {
  return {
    id: row.id,
    kind: row.kind,
    title: row.title,
    lastMessageAt: row.last_message_at ? row.last_message_at.toISOString() : null,
    otherUserId: row.other_user_id,
    otherUserPhoneE164: row.other_user_phone_e164,
    otherUserDisplayName: row.other_user_display_name,
  };
}

export interface FileSearchResultDto {
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

export function toFileSearchResultDto(row: FileSearchRow): FileSearchResultDto {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    uploaderId: row.uploader_id,
    kind: row.kind,
    originalFilename: row.original_filename,
    mimeType: row.mime_type,
    sizeBytes: Number(row.size_bytes),
    widthPx: row.width_px,
    heightPx: row.height_px,
    durationMs: row.duration_ms,
    hasThumbnail: row.thumbnail_storage_key !== null,
    createdAt: row.created_at.toISOString(),
  };
}

export interface SearchResultsDto {
  messages: MessageSearchResultDto[];
  conversations: ConversationSearchResultDto[];
  files: FileSearchResultDto[];
}
