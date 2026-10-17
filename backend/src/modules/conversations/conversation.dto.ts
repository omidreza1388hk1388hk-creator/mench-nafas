import { ConversationRow, OtherMemberRow, MemberRow } from './conversations.repository';

/**
 * Client-facing camelCase shape. ConversationRow is the raw DB row
 * (snake_case, Date objects) — returning it directly to clients, as an
 * earlier draft of this controller did, is inconsistent with every other
 * endpoint in this API (see MessageDto in the messages module) and would
 * silently fail to deserialize on the Android side, since Moshi matches
 * JSON keys to Kotlin property names literally.
 */
export interface ConversationDto {
  id: string;
  kind: string;
  title: string | null;
  createdBy: string | null;
  createdAt: string;
  lastMessageAt: string | null;
  otherUserId: string | null;
  otherUserPhoneE164: string | null;
  otherUserDisplayName: string | null;
  /** The other person's profile-photo version (ms timestamp, for ?v= cache busting); null = no photo. */
  otherUserAvatarVersion: number | null;
  /** Per-requesting-user settings. Only meaningful in the caller's own list/create responses; broadcast copies (sent to other members) always carry false. */
  isMuted: boolean;
  isPinned: boolean;
  isArchived: boolean;
  /** Phase 9 — shared 'us' dates (YYYY-MM-DD), direct conversations only. */
  togetherSince: string | null;
  /** Phase 11 — the pinned message's server id (the client looks the text up locally), or null. */
  pinnedMessageId: string | null;
  nextMeetDate: string | null;
}

export function toConversationDto(row: ConversationRow, otherMember: OtherMemberRow | null): ConversationDto {
  return {
    id: row.id,
    kind: row.kind,
    title: row.title,
    createdBy: row.created_by,
    createdAt: row.created_at.toISOString(),
    lastMessageAt: row.last_message_at ? row.last_message_at.toISOString() : null,
    otherUserId: otherMember?.id ?? null,
    otherUserPhoneE164: otherMember?.phone_e164 ?? null,
    otherUserDisplayName: otherMember?.display_name ?? null,
    otherUserAvatarVersion: otherMember?.avatar_version ? Number(otherMember.avatar_version) : null,
    isMuted: row.is_muted ?? false,
    isPinned: row.is_pinned ?? false,
    isArchived: row.is_archived ?? false,
    togetherSince: row.together_since ?? null,
    pinnedMessageId: row.pinned_message_id ?? null,
    nextMeetDate: row.next_meet_date ?? null,
  };
}

/** MemberRow's client-facing camelCase shape — same reasoning as ConversationDto above. */
export interface ConversationMemberDto {
  userId: string;
  phoneE164: string;
  displayName: string | null;
  role: string;
  /** Profile-photo version (ms timestamp, cache-buster); null = no photo. */
  avatarVersion: number | null;
}

export function toConversationMemberDto(row: MemberRow): ConversationMemberDto {
  return {
    userId: row.user_id,
    phoneE164: row.phone_e164,
    displayName: row.display_name,
    role: row.role,
    avatarVersion: row.avatar_version ? Number(row.avatar_version) : null,
  };
}
