package com.omidgame.mench.feature.calls.data

import com.omidgame.mench.core.network.CallsApi
import com.omidgame.mench.core.network.EndCallBody
import com.omidgame.mench.core.network.InitiateCallBody
import com.omidgame.mench.core.network.CallSessionResponse
import com.omidgame.mench.core.network.CallHistorySummaryResponse
import com.omidgame.mench.core.network.realtime.RealtimeClient
import com.omidgame.mench.core.network.realtime.ServerToClientEvent
import com.omidgame.mench.core.network.realtime.ClientToServerEvent
import com.omidgame.mench.feature.calls.domain.CallHistoryEntry
import com.omidgame.mench.feature.calls.domain.CallRepository
import com.omidgame.mench.feature.calls.domain.CallSession
import com.omidgame.mench.feature.calls.domain.CallSignalEvent
import com.omidgame.mench.feature.calls.domain.CallStatus
import com.omidgame.mench.feature.calls.domain.CallType
import com.omidgame.mench.feature.calls.domain.IceServerConfig
import com.omidgame.mench.feature.calls.domain.LocalIceCandidate
import com.omidgame.mench.feature.chat.domain.ChatRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns raw ServerToClientEvent.Call* frames from RealtimeClient (shared
 * across every feature that listens to /ws — chat, typing, and now calls)
 * into this feature's own CallSignalEvent, and turns REST responses into
 * this feature's own CallSession — the presentation layer (CallViewModel/
 * CallSessionManager) never touches CallsApi's or RealtimeClient's wire
 * types directly, same layering as ChatRepositoryImpl.
 */
@Singleton
class CallRepositoryImpl @Inject constructor(
    private val callsApi: CallsApi,
    private val realtimeClient: RealtimeClient,
    private val chatRepository: ChatRepository,
) : CallRepository {

    private val scope = CoroutineScope(SupervisorJob())

    // replay = 0: a signal that fired before a collector subscribed is
    // stale (the call state has already moved on) — replaying it would
    // hand CallSessionManager an out-of-date event on every fresh
    // collection, e.g. right after process start.
    private val _signals = MutableSharedFlow<CallSignalEvent>(replay = 0, extraBufferCapacity = 16)
    override val signals: SharedFlow<CallSignalEvent> = _signals

    init {
        scope.launch {
            realtimeClient.events.collect { event ->
                mapToSignal(event)?.let { _signals.emit(it) }
            }
        }
    }

    override suspend fun startCall(conversationId: String, callType: CallType): CallSession {
        val response = callsApi.initiate(InitiateCallBody(conversationId, callType.wire()))
        return response.toDomain(selfUserId = chatRepository.currentUserId())
    }

    override suspend fun acceptCall(callId: String): CallSession {
        val response = callsApi.accept(callId)
        return response.toDomain(selfUserId = chatRepository.currentUserId())
    }

    override suspend fun declineCall(callId: String) {
        callsApi.decline(callId)
    }

    override suspend fun endCall(callId: String, reason: String?) {
        callsApi.end(callId, EndCallBody(reason))
    }

    override suspend fun listHistory(before: String?, limit: Int): List<CallHistoryEntry> =
        callsApi.listHistory(before, limit).map { it.toDomain() }

    override fun sendOffer(callId: String, sdp: String) {
        realtimeClient.send(ClientToServerEvent.CallOffer(callId, sdp))
    }

    override fun sendAnswer(callId: String, sdp: String) {
        realtimeClient.send(ClientToServerEvent.CallAnswer(callId, sdp))
    }

    override fun sendIceCandidate(callId: String, candidate: LocalIceCandidate) {
        realtimeClient.send(
            ClientToServerEvent.CallIceCandidate(callId, candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex),
        )
    }

    override fun sendHangupSignal(callId: String) {
        realtimeClient.send(ClientToServerEvent.CallHangup(callId))
    }

    private fun mapToSignal(event: ServerToClientEvent): CallSignalEvent? = when (event) {
        is ServerToClientEvent.CallIncoming -> CallSignalEvent.Incoming(
            callId = event.call.id,
            conversationId = event.call.conversationId,
            callerId = event.call.callerId,
            callType = event.call.callType.toCallType(),
            callerDisplayName = event.call.callerDisplayName,
            callerPhoneE164 = event.call.callerPhoneE164,
        )
        is ServerToClientEvent.CallAccepted -> CallSignalEvent.Accepted(event.callId)
        is ServerToClientEvent.CallDeclined -> CallSignalEvent.Declined(event.callId)
        is ServerToClientEvent.CallEnded -> CallSignalEvent.Ended(event.callId, event.status.toCallStatus())
        is ServerToClientEvent.CallOffer -> CallSignalEvent.RemoteOffer(event.callId, event.sdp)
        is ServerToClientEvent.CallAnswer -> CallSignalEvent.RemoteAnswer(event.callId, event.sdp)
        is ServerToClientEvent.CallIceCandidate -> CallSignalEvent.RemoteIceCandidate(
            event.callId, event.candidate, event.sdpMid, event.sdpMLineIndex,
        )
        is ServerToClientEvent.CallHangup -> CallSignalEvent.RemoteHangup(event.callId)
        else -> null // not a call.* frame — this collector only cares about those
    }
}

private fun CallType.wire(): String = when (this) {
    CallType.VOICE -> "voice"
    CallType.VIDEO -> "video"
}

private fun String.toCallType(): CallType = if (this == "video") CallType.VIDEO else CallType.VOICE

private fun String.toCallStatus(): CallStatus = when (this) {
    "active" -> CallStatus.ACTIVE
    "ended" -> CallStatus.ENDED
    "declined" -> CallStatus.DECLINED
    "missed" -> CallStatus.MISSED
    else -> CallStatus.RINGING
}

private fun CallSessionResponse.toDomain(selfUserId: String?): CallSession {
    val isOutgoing = selfUserId != null && selfUserId == callerId
    val otherUserId = if (isOutgoing) calleeId else callerId
    return CallSession(
        id = id,
        conversationId = conversationId,
        callerId = callerId,
        calleeId = calleeId,
        callType = callType.toCallType(),
        status = status.toCallStatus(),
        iceServers = iceServers.map { IceServerConfig(it.urls, it.username, it.credential) },
        isOutgoing = isOutgoing,
        otherUserId = otherUserId,
        // The REST response doesn't carry the other party's display name
        // for the *caller* side (only call.incoming does, for the
        // callee) — CallSessionManager fills this in from the
        // call.incoming signal when it has one, and otherwise falls back
        // to showing the phone number only. Not fetched here via an
        // extra UsersApi round trip, to keep call setup latency low.
        otherDisplayName = null,
        otherPhoneE164 = "",
    )
}

private fun CallHistorySummaryResponse.toDomain(): CallHistoryEntry = CallHistoryEntry(
    id = id,
    conversationId = conversationId,
    callType = callType.toCallType(),
    status = status.toCallStatus(),
    startedAtIso = startedAt,
    durationSeconds = durationSeconds,
    wasOutgoing = wasOutgoing,
    otherUserId = otherUserId,
    otherDisplayName = otherUserDisplayName,
    otherPhoneE164 = otherUserPhoneE164,
)
