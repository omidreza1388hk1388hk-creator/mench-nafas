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
    val hasThumbnail: Boolean,
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

    @JsonClass(generateAdapter = true)
    data class MessageRead(
        val conversationId: String,
        val userId: String,
        val lastReadSequence: Long,
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
}
