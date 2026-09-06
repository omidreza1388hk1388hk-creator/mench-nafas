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
}

/** Local (not-yet-sent-to-the-network-layer) ICE candidate shape — kept independent of org.webrtc.IceCandidate so the domain/data layers don't import the WebRTC SDK directly (only core/webrtc does). */
data class LocalIceCandidate(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String)
