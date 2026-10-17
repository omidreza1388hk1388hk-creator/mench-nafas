package com.omidgame.mench.core.network.realtime

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Mirrors backend/src/modules/realtime/realtime-events.ts exactly — same
 * flat `type` discriminator field, same field names. Any change to the
 * backend's wire shape must be mirrored here by hand; there is no shared
 * schema source of truth between the Kotlin and TypeScript sides yet.
 */
@JsonClass(generateAdapter = true)
data class MessageAttachmentSummaryWire(
    val id: String,
    val kind: String,
    val mimeType: String,
    val originalFilename: String,
    val sizeBytes: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val durationMs: Long?,
    val hasThumbnail: Boolean,
    val blurHash: String? = null,
    val waveform: String? = null,
)

@JsonClass(generateAdapter = true)
data class ReactionSummaryWire(
    val emoji: String,
    val userIds: List<String>,
)

@JsonClass(generateAdapter = true)
data class MessageWire(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val clientMsgId: String,
    val kind: String,
    val body: String?,
    val attachment: MessageAttachmentSummaryWire?,
    val sequence: Long,
    val createdAt: String,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val forwardedFromMessageId: String? = null,
    val replyToMessageId: String? = null,
    val reactions: List<ReactionSummaryWire> = emptyList(),
)

/** Mirrors backend/src/modules/conversations/conversation.dto.ts's ConversationDto — a separate type from ConversationResponse (core.network.ChatApi) on purpose, matching this file's existing REST-vs-realtime wire-type split (see MessageWire vs MessageResponse). */
@JsonClass(generateAdapter = true)
data class ConversationWire(
    val id: String,
    val kind: String,
    val title: String?,
    val createdBy: String?,
    val createdAt: String,
    val lastMessageAt: String?,
    val otherUserId: String?,
    val otherUserPhoneE164: String?,
    val otherUserDisplayName: String?,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    /** Phase 11 — server id of the pinned message, null = none. */
    val pinnedMessageId: String? = null,
    val togetherSince: String? = null,
    val nextMeetDate: String? = null,
    /** Phase 10 — the other person's profile-photo version (ms), null = no photo. */
    val otherUserAvatarVersion: Long? = null,
)

/** Mirrors backend/src/modules/realtime/realtime-events.ts's CallIncomingSummary. */
@JsonClass(generateAdapter = true)
data class CallIncomingSummaryWire(
    val id: String,
    val conversationId: String,
    val callerId: String,
    val callType: String,
    val callerDisplayName: String?,
    val callerPhoneE164: String,
)

sealed interface ServerToClientEvent {
    @JsonClass(generateAdapter = true)
    data class MessageCreated(@Json(name = "message") val message: MessageWire) : ServerToClientEvent

    /** Body edit only — see the backend's realtime-events.ts for why deletion has its own event instead of reusing this one. */
    @JsonClass(generateAdapter = true)
    data class MessageUpdated(@Json(name = "message") val message: MessageWire) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class MessageDeleted(
        val conversationId: String,
        val messageId: String,
        val deletedAt: String,
    ) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class ReactionUpdated(
        val conversationId: String,
        val messageId: String,
        val reactions: List<ReactionSummaryWire>,
    ) : ServerToClientEvent

    /** Phase 11. The other person's device received our messages up to this sequence. */
    @JsonClass(generateAdapter = true)
    data class MessageDelivered(
        val conversationId: String,
        val userId: String,
        val lastDeliveredSequence: Long,
    ) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class MessageRead(
        val conversationId: String,
        val userId: String,
        val lastReadSequence: Long,
    ) : ServerToClientEvent

    /** Phase 9. A shared 'us' date changed (either person can set them). */
    @JsonClass(generateAdapter = true)
    data class UsUpdated(
        val conversationId: String,
        val togetherSince: String?,
        val nextMeetDate: String?,
    ) : ServerToClientEvent

    /** Phase 2A. A poll changed (vote / closed); carries the whole poll. */
    @JsonClass(generateAdapter = true)
    data class PollUpdated(val conversationId: String, val poll: com.omidgame.mench.core.network.PollWire) : ServerToClientEvent

    /** Phase 11. The pinned message of a conversation changed (null = unpinned). */
    @JsonClass(generateAdapter = true)
    data class PinChanged(val conversationId: String, val messageId: String?) : ServerToClientEvent

    /** Phase 4C. The disappearing-messages timer of a conversation changed (seconds null = off). */
    @JsonClass(generateAdapter = true)
    data class DisappearingChanged(
        val conversationId: String,
        val seconds: Int?,
        val changedBy: String,
    ) : ServerToClientEvent

    /** Phase 11. The partner answered today's question. */
    @JsonClass(generateAdapter = true)
    data class QuestionAnswered(val conversationId: String) : ServerToClientEvent

    /** Phase 11. The shared wishlist changed. */
    @JsonClass(generateAdapter = true)
    data class WishlistChanged(val conversationId: String) : ServerToClientEvent

    /** Phase 9. The partner set today's mood. */
    @JsonClass(generateAdapter = true)
    data class MoodChanged(
        val conversationId: String,
        val userId: String,
        val emoji: String,
        val text: String?,
        val setAt: String,
    ) : ServerToClientEvent

    /** Phase 3. A photo was added to, reacted to, or removed from the shared album. */
    @JsonClass(generateAdapter = true)
    data class AlbumChanged(val conversationId: String) : ServerToClientEvent

    /** Phase 3A. A quiz was added or deleted, or your quiz was answered. */
    @JsonClass(generateAdapter = true)
    data class QuizChanged(val conversationId: String) : ServerToClientEvent

    /** Phase 3. The partner added or deleted a vault note. */
    @JsonClass(generateAdapter = true)
    data class VaultChanged(val conversationId: String) : ServerToClientEvent

    /** Phase 3B. A story was added or deleted, or your story was seen. */
    @JsonClass(generateAdapter = true)
    data class StoryChanged(val conversationId: String) : ServerToClientEvent

    /** Phase 3A. The partner drew on, or cleared, the shared canvas. */
    @JsonClass(generateAdapter = true)
    data class CanvasChanged(val conversationId: String) : ServerToClientEvent

    /** Phase 3A. The partner added, bumped or removed a shared counter. */
    @JsonClass(generateAdapter = true)
    data class CounterChanged(val conversationId: String) : ServerToClientEvent

    /** Phase 9. The partner added/removed a shared memory. */
    @JsonClass(generateAdapter = true)
    data class MemoryChanged(val conversationId: String) : ServerToClientEvent

    /** Phase 9. A hug or kiss from the partner ([kind] = "hug" | "kiss"). */
    @JsonClass(generateAdapter = true)
    data class AffectionReceived(val conversationId: String, val from: String, val kind: String) : ServerToClientEvent

    /** Phase 9. A sealed letter was written to this user. */
    @JsonClass(generateAdapter = true)
    data class LetterReceived(val conversationId: String, val letterId: String) : ServerToClientEvent

    /** Phase 9. The partner opened a letter this user wrote. */
    @JsonClass(generateAdapter = true)
    data class LetterOpened(val conversationId: String, val letterId: String) : ServerToClientEvent

    /** Phase 9. The partner tapped "I miss you". */
    @JsonClass(generateAdapter = true)
    data class MissYou(val conversationId: String, val from: String) : ServerToClientEvent

    /** Phase 3A. A live heartbeat from the partner (press-and-hold heart); the client vibrates in rhythm and plays a full-screen pulse for [durationMs]. */
    @JsonClass(generateAdapter = true)
    data class Heartbeat(val conversationId: String, val from: String, val durationMs: Long) : ServerToClientEvent

    /** Phase 3A. A full-screen effect the sender picked by hand; [effect] is MessageEffect.wire. */
    @JsonClass(generateAdapter = true)
    data class ScreenEffect(val conversationId: String, val from: String, val effect: String) : ServerToClientEvent

    /** Phase 3: Together mode (live-synced playback). */
    data class Together(
        val conversationId: String,
        val from: String,
        val kind: String,
        val attachmentId: String?,
        val positionMs: Long,
        val playing: Boolean,
        val sentAt: Long,
    ) : ServerToClientEvent

    /** Phase 8. Online/offline transition (or per-contact snapshot right after connecting) for someone sharing a conversation with this user. lastSeenAt is ISO-8601, null if never seen. */
    @JsonClass(generateAdapter = true)
    data class PresenceChanged(
        val userId: String,
        val online: Boolean,
        val lastSeenAt: String?,
    ) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class TypingStarted(val conversationId: String, val userId: String) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class TypingStopped(val conversationId: String, val userId: String) : ServerToClientEvent

    // --- Phase 6: group realtime completion ---

    /** Sent only to a member newly added to a group — carries the full conversation so the client can insert it into Room without a follow-up REST round trip. See backend realtime-events.ts's doc comment on this event for why existing members get GroupMemberAdded instead. */
    @JsonClass(generateAdapter = true)
    data class ConversationAdded(val conversation: ConversationWire) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class GroupMemberAdded(
        val conversationId: String,
        val userId: String,
        val addedBy: String,
    ) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class GroupMemberRemoved(
        val conversationId: String,
        val userId: String,
        val removedBy: String,
    ) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class GroupMemberLeft(val conversationId: String, val userId: String) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class GroupRenamed(
        val conversationId: String,
        val title: String,
        val renamedBy: String,
    ) : ServerToClientEvent

    // --- Phase 6: calls ---

    @JsonClass(generateAdapter = true)
    data class CallIncoming(val call: CallIncomingSummaryWire) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class CallAccepted(val callId: String, val by: String) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class CallDeclined(val callId: String, val by: String) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class CallEnded(val callId: String, val by: String, val status: String, val reason: String?) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class CallOffer(val callId: String, val from: String, val sdp: String) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class CallAnswer(val callId: String, val from: String, val sdp: String) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class CallIceCandidate(
        val callId: String,
        val from: String,
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?,
    ) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class CallHangup(val callId: String, val from: String) : ServerToClientEvent

    /** Phase 2B. In-call chat line / emoji reaction from the other participant. */
    @JsonClass(generateAdapter = true)
    data class CallChat(val callId: String, val from: String, val text: String, val sentAt: String) : ServerToClientEvent

    @JsonClass(generateAdapter = true)
    data class CallReaction(val callId: String, val from: String, val emoji: String) : ServerToClientEvent
}

sealed interface ClientToServerEvent {
    val type: String

    data class TypingStarted(val conversationId: String) : ClientToServerEvent {
        override val type = "typing.started"
    }

    data class TypingStopped(val conversationId: String) : ClientToServerEvent {
        override val type = "typing.stopped"
    }

    data class CallOffer(val callId: String, val sdp: String) : ClientToServerEvent {
        override val type = "call.offer"
    }

    data class CallAnswer(val callId: String, val sdp: String) : ClientToServerEvent {
        override val type = "call.answer"
    }

    data class CallIceCandidate(
        val callId: String,
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?,
    ) : ClientToServerEvent {
        override val type = "call.ice-candidate"
    }

    data class CallHangup(val callId: String) : ClientToServerEvent {
        override val type = "call.hangup"
    }

    data class CallChat(val callId: String, val text: String) : ClientToServerEvent {
        override val type = "call.chat"
    }

    data class CallReaction(val callId: String, val emoji: String) : ClientToServerEvent {
        override val type = "call.reaction"
    }
}
