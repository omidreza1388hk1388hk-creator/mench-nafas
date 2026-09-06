package com.omidgame.mench.feature.calls.domain

import kotlinx.coroutines.flow.SharedFlow

interface CallRepository {
    suspend fun startCall(conversationId: String, callType: CallType): CallSession
    suspend fun acceptCall(callId: String): CallSession
    suspend fun declineCall(callId: String)
    suspend fun endCall(callId: String, reason: String? = null)
    suspend fun listHistory(before: String? = null, limit: Int = 50): List<CallHistoryEntry>

    /** Live call.* frames from the /ws connection — see RealtimeClient.events, filtered to just the call.* subset and mapped to CallSignalEvent. */
    val signals: SharedFlow<CallSignalEvent>

    fun sendOffer(callId: String, sdp: String)
    fun sendAnswer(callId: String, sdp: String)
    fun sendIceCandidate(callId: String, candidate: LocalIceCandidate)
    /** Fast-path relay only — endCall() above is still what makes the hangup authoritative server-side; see the backend's chat.gateway.ts doc comment on call.hangup. */
    fun sendHangupSignal(callId: String)
}
