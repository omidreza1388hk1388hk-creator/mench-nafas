package com.omidgame.mench.feature.calls.domain

enum class CallType { VOICE, VIDEO }

enum class CallStatus { RINGING, ACTIVE, ENDED, DECLINED, MISSED }

data class IceServerConfig(val urls: List<String>, val username: String?, val credential: String?)

/** What the app actually needs to run a call, once it's been created/accepted server-side — the REST response, trimmed to what WebRtcClient and the UI use. */
data class CallSession(
    val id: String,
    val conversationId: String,
    val callerId: String,
    val calleeId: String,
    val callType: CallType,
    val status: CallStatus,
    val iceServers: List<IceServerConfig>,
    /** True if the current device is the caller — decides whether this device should createOffer (caller) or wait for one (callee). */
    val isOutgoing: Boolean,
    val otherUserId: String,
    val otherDisplayName: String?,
    val otherPhoneE164: String,
)

data class CallHistoryEntry(
    val id: String,
    val conversationId: String,
    val callType: CallType,
    val status: CallStatus,
    val startedAtIso: String,
    val durationSeconds: Int?,
    val wasOutgoing: Boolean,
    val otherUserId: String,
    val otherDisplayName: String?,
    val otherPhoneE164: String,
)

/** Everything relayed live over /ws for an in-progress call — mirrors the backend's call.* ServerToClientEvent subset (see RealtimeEvents.kt). */
sealed interface CallSignalEvent {
    data class Incoming(
        val callId: String,
        val conversationId: String,
        val callerId: String,
        val callType: CallType,
        val callerDisplayName: String?,
        val callerPhoneE164: String,
    ) : CallSignalEvent

    data class Accepted(val callId: String) : CallSignalEvent
    data class Declined(val callId: String) : CallSignalEvent
    data class Ended(val callId: String, val status: CallStatus) : CallSignalEvent
    data class RemoteOffer(val callId: String, val sdp: String) : CallSignalEvent
    data class RemoteAnswer(val callId: String, val sdp: String) : CallSignalEvent
    data class RemoteIceCandidate(
        val callId: String,
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?,
    ) : CallSignalEvent
    data class RemoteHangup(val callId: String) : CallSignalEvent
    /** Phase 2B */
    data class RemoteChat(val callId: String, val text: String, val sentAtIso: String) : CallSignalEvent
    data class RemoteReaction(val callId: String, val emoji: String) : CallSignalEvent
}

/** Phase 2B — one line of the in-call chat (kept only in memory for the duration of the call). */
data class CallChatLine(val fromMe: Boolean, val text: String, val atMillis: Long)

/** Phase 2B — an emoji reaction to show floating up the screen; [id] makes each one distinct for the UI. */
data class CallReaction(val id: Long, val emoji: String, val fromMe: Boolean)

/** The reactions offered in the call (must match the backend's CALL_REACTIONS list). */
val CALL_REACTION_EMOJI = listOf("\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDC4D", "\uD83D\uDD25", "\uD83D\uDE18", "\uD83E\uDD70", "\uD83D\uDC4F")

/** Local (not-yet-sent-to-the-network-layer) ICE candidate shape — kept independent of org.webrtc.IceCandidate so the domain/data layers don't import the WebRTC SDK directly (only core/webrtc does). */
data class LocalIceCandidate(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String)
