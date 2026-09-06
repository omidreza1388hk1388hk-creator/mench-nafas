package com.omidgame.mench.feature.chat.domain

/**
 * Domain-layer message state — deliberately a separate type from
 * core.database.MessageState, even though the values are identical today.
 * Keeps the domain/presentation layers from depending on a data-layer
 * (Room) type, matching the AuthRepository/AuthResult separation from
 * Phase 1. ChatRepositoryImpl maps between the two at the boundary.
 */
enum class DomainMessageState {
    PENDING,
    SENDING,
    SENT,
    READ,
    FAILED,
}

enum class DomainMessageKind {
    TEXT,
    IMAGE,
    FILE,
    AUDIO,
    VIDEO,
}

data class MessageAttachment(
    val id: String?, // null until the Outbox upload completes — see localContentUri
    val mimeType: String,
    val originalFilename: String,
    val sizeBytes: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val durationMs: Long?,
    val hasThumbnail: Boolean,
    /** Local file path (content already on-device — the source before upload, or a downloaded copy after). Null if not yet cached locally. */
    val localContentUri: String?,
    val localThumbnailUri: String?,
)

data class Conversation(
    val id: String,
    /** "direct" or "group" — see DomainMessageKind's sibling comment; kept as a raw string (not an enum) since nothing branches on it besides a straightforward direct/group check today. */
    val kind: String,
    /** Non-null only for kind == "group" — a direct conversation's display name comes from otherUserDisplayName/otherUserPhoneE164 instead (see ConversationRow.title's doc comment on the backend). */
    val title: String?,
    val otherUserDisplayName: String?,
    val otherUserPhoneE164: String?,
    val lastMessageAtEpochMillis: Long?,
    val lastReadSequence: Long,
) {
    val isGroup: Boolean get() = kind == "group"
}

data class ConversationMember(
    val userId: String,
    val phoneE164: String,
    val displayName: String?,
    val role: String,
) {
    val isOwner: Boolean get() = role == "owner"
}

/** userIds is the full reactor list, not just a count — see backend ReactionSummary for why. reactedByMe is derived at the mapping boundary (ChatMappers), not stored separately, so it can never drift from userIds. */
data class ReactionSummary(
    val emoji: String,
    val userIds: List<String>,
    val reactedByMe: Boolean,
)

data class Message(
    val clientMsgId: String,
    /** Null until the server has confirmed this send (see MessageState.PENDING/SENDING) — edit/delete/react/forward all require this to be non-null, since those operations are keyed by the server's message id, not the local clientMsgId. */
    val serverId: String?,
    val conversationId: String,
    val senderId: String,
    val kind: DomainMessageKind,
    val body: String?,
    val attachment: MessageAttachment?,
    val sequence: Long?,
    val createdAtEpochMillis: Long,
    val state: DomainMessageState,
    val isOwn: Boolean,
    val editedAtEpochMillis: Long? = null,
    /** Non-null means deleted — the UI shows a placeholder and ignores body/attachment, same contract as MessageEntity.deletedAtEpochMillis. */
    val deletedAtEpochMillis: Long? = null,
    val forwardedFromMessageId: String? = null,
    val reactions: List<ReactionSummary> = emptyList(),
)

sealed interface ChatResult<out T> {
    data class Success<T>(val value: T) : ChatResult<T>
    data class Failure(val reason: ChatFailureReason) : ChatResult<Nothing>
}

enum class ChatFailureReason {
    NETWORK_UNAVAILABLE,
    USER_NOT_FOUND,
    CANNOT_MESSAGE_SELF,
    /** Group title was blank, or a phone number in the invite list didn't resolve to a MENCH user. */
    INVALID_GROUP,
    /** Removing/renaming attempted by someone who isn't the group owner. */
    NOT_GROUP_OWNER,
    UNKNOWN,
}
