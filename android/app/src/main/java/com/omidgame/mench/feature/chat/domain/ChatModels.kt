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
    DELIVERED,
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
    /** BlurHash placeholder from the server (null for own just-sent media and non-image files). */
    val blurHash: String? = null,
    /** Phase 2A — voice messages only: ~48 bars, each 0..255. Null = none recorded (older clients); the UI then draws a neutral fallback. */
    val waveform: List<Int>? = null,
)

/** One person who has read one of my messages in a group (Phase 2A). */
data class SeenBy(val userId: String, val name: String, val readAtEpochMillis: Long)

/** Phase 2: one earlier version of an edited message. */
/** Phase 3: one live Together-mode event from the partner (start / sync / stop of a shared playback). */
data class TogetherSignal(
    val kind: String,
    val attachmentId: String?,
    val positionMs: Long,
    val playing: Boolean,
    val sentAtEpochMillis: Long,
)

data class EditVersion(val body: String, val replacedAtEpochMillis: Long)

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
    /** Direct chats only — the other person's user id, used to look up their live presence. */
    val otherUserId: String? = null,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val pinnedMessageId: String? = null,
    /** Phase 9 — shared dates, ISO yyyy-MM-dd; direct chats only. */
    val togetherSince: String? = null,
    val nextMeetDate: String? = null,
    /** Phase 10 — other person's profile-photo version (direct chats), null = none. */
    val otherUserAvatarVersion: Long? = null,
) {
    val isGroup: Boolean get() = kind == "group"

    /** The private Saved messages chat (a one-member conversation). */
    val isSaved: Boolean get() = kind == "saved"
}

/** Chat-list preview: the latest message and how many received messages are still unread. */
data class ConversationPreview(
    val lastBody: String?,
    val lastKind: DomainMessageKind,
    val lastIsOwn: Boolean,
    val lastIsDeleted: Boolean,
    val lastAtEpochMillis: Long,
    val unreadCount: Int,
)

/** Phase 9 — one person's mood for the day it was set ([setAtEpochMillis]). */
data class Mood(val emoji: String, val text: String?, val setAtEpochMillis: Long)

/** Phase 9 — both people's moods in a direct conversation (null = not set). */
data class Moods(val mine: Mood?, val other: Mood?)

/** Phase 11 — a page title/description fetched by the server for a link in a message. */
data class LinkPreview(val url: String, val title: String?, val description: String?, val siteName: String?)

/** Phase 11 — today's question and both answers (the partner's is hidden until you answer). */
data class Question(
    val date: String,
    val question: String,
    val myAnswer: String?,
    val partnerAnswered: Boolean,
    val partnerAnswer: String?,
    val streak: Int = 0,
    val bestStreak: Int = 0,
)

/** Phase 11 — one wishlist entry ("things to do together"). */
data class Wish(val id: String, val title: String, val done: Boolean, val isMine: Boolean)

/** Phase 3A — quiz list entry. [myScore]/[myTotal] are yours on someone else's quiz; [partnerScore]/[partnerTotal] on yours. */
data class QuizSummary(
    val id: String,
    val title: String,
    val isMine: Boolean,
    val questionCount: Int,
    val myScore: Int?,
    val myTotal: Int?,
    val partnerScore: Int?,
    val partnerTotal: Int?,
)

/** [correct] is null until the server allows you to see it (you wrote the quiz, or you already answered). */
data class QuizQuestion(val text: String, val options: List<String>, val correct: Int?)

data class QuizDetail(val id: String, val title: String, val isMine: Boolean, val answered: Boolean, val questions: List<QuizQuestion>)

data class QuizResult(val score: Int, val total: Int, val correct: List<Int>)

/** A photo the user picked from the device (content URI, MIME type, file name), waiting to be uploaded with a letter, story or album entry. */
data class PickedPhoto(val uri: String, val mimeType: String, val filename: String?)

/** Phase 3 — a photo in the shared album, with each person's single reaction. */
data class AlbumItem(
    val id: String,
    val attachmentId: String,
    val caption: String,
    val isMine: Boolean,
    val myReaction: String?,
    val otherReaction: String?,
)

/** Phase 3 — an encrypted vault note: [blob] is decrypted on the phone, never by the server. */
data class VaultItem(val id: String, val blob: String, val isMine: Boolean)

/** Phase 3B — a 24-hour text story. [seen] is for stories by others; [views] for your own. */
data class Story(
    val id: String,
    val body: String,
    val emoji: String,
    val palette: Int,
    val isMine: Boolean,
    val seen: Boolean,
    val views: Int,
    /** Optional photo shown behind the text. */
    val attachmentId: String? = null,
)

/** Phase 3A — one stroke on the shared canvas; [points] is flat x,y pairs normalised 0..1, [color] is "#rrggbb". */
data class CanvasStroke(val color: String, val width: Float, val points: List<Float>)

/** Phase 3A — a shared counter. [kind] is "date" (count from/to [anchorDate], ISO yyyy-MM-dd) or "tally" ([tallyCount]). */
data class Counter(
    val id: String,
    val title: String,
    val emoji: String,
    val kind: String,
    val anchorDate: String?,
    val tallyCount: Int,
    val isMine: Boolean,
    /** Card gradient 0..5 (0 = plain). */
    val theme: Int = 0,
)

/** Phase 9 — a shared memory. [memoryDate] is ISO yyyy-MM-dd; [attachmentId] an optional photo. */
data class Memory(
    val id: String,
    val title: String,
    val note: String?,
    val memoryDate: String,
    val attachmentId: String?,
    val isMine: Boolean,
)

/** Phase 9 — an "open when…" letter. [body] is null while sealed (recipient side). Times are epoch millis. */
data class Letter(
    val id: String,
    val title: String,
    val body: String?,
    val unlockAtEpochMillis: Long?,
    val openedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val isMine: Boolean,
    val canOpen: Boolean,
    /** Optional photo; for a received letter the server only sends it after the letter is opened. */
    val attachmentId: String? = null,
)

/** Live presence of one user. lastSeenAtEpochMillis is null if never seen (or unknown yet). */
data class UserPresence(
    val online: Boolean,
    val lastSeenAtEpochMillis: Long?,
)

data class ConversationMember(
    val userId: String,
    val phoneE164: String,
    val displayName: String?,
    val role: String,
    val avatarVersion: Long? = null,
) {
    val isOwner: Boolean get() = role == "owner"
    val isAdmin: Boolean get() = role == "admin"
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
    /** Set when this message is a reply — points at the serverId of the quoted message, within the same conversation. Null for a normal (non-reply) message. */
    val replyToMessageId: String? = null,
    val reactions: List<ReactionSummary> = emptyList(),
)

/** Phase 2A: a message waiting to be sent at [sendAtEpochMillis]. */
data class ScheduledMessage(val id: String, val body: String, val silent: Boolean, val sendAtEpochMillis: Long)

/** Phase 2A: one poll option with everybody who currently voted for it. */
data class PollOption(val id: String, val text: String, val voterIds: List<String>)

data class Poll(
    val id: String,
    val conversationId: String,
    val creatorId: String,
    val question: String,
    val multiple: Boolean,
    val closed: Boolean,
    val options: List<PollOption>,
) {
    /** Distinct people who voted (a multiple-choice voter counts once). */
    val totalVoters: Int get() = options.flatMap { it.voterIds }.toSet().size
    fun myChoices(selfUserId: String?): Set<String> =
        if (selfUserId == null) emptySet() else options.filter { selfUserId in it.voterIds }.mapTo(HashSet()) { it.id }

    /** Optimistic copy of this poll with [selfUserId]'s votes replaced by [optionIds] (the server answer overwrites it). */
    fun withMyVotes(selfUserId: String, optionIds: Collection<String>): Poll = copy(
        options = options.map { option ->
            val others = option.voterIds.filter { it != selfUserId }
            option.copy(voterIds = if (option.id in optionIds) others + selfUserId else others)
        },
    )

    /**
     * The option ids a tap on [optionId] should send. Single choice: tapping the chosen option
     * retracts, otherwise it replaces. Multiple choice: toggles that option in the current set.
     */
    fun choicesAfterTap(optionId: String, selfUserId: String?): List<String> {
        val current = myChoices(selfUserId)
        return if (multiple) {
            (if (optionId in current) current - optionId else current + optionId).toList()
        } else {
            if (optionId in current) emptyList() else listOf(optionId)
        }
    }
}

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
