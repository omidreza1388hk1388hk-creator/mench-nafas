package com.omidgame.mench.feature.calls.domain

import android.content.Context
import android.util.Log
import com.omidgame.mench.core.webrtc.CallAudioRouter
import com.omidgame.mench.core.webrtc.ProximityScreenOff
import com.omidgame.mench.core.webrtc.WebRtcClient
import com.omidgame.mench.core.webrtc.WebRtcClientFactory
import com.omidgame.mench.core.webrtc.WebRtcClientListener
import com.omidgame.mench.feature.calls.service.CallForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one orchestrator for the whole calls feature — every screen
 * (ChatScreen's call buttons, the incoming/outgoing/in-call UI) reads
 * `state` and calls into this rather than touching CallRepository or
 * WebRtcClient directly. @Singleton, so it survives navigation and keeps
 * exactly one call's worth of state regardless of how many CallViewModel
 * instances get created/destroyed as the user moves between screens —
 * see MenchNavGraph for how the overlay is wired to this single instance.
 */
@Singleton
class CallSessionManager @Inject constructor(
    private val callRepository: CallRepository,
    private val webRtcClientFactory: WebRtcClientFactory,
    private val audioRouter: CallAudioRouter,
    private val proximity: ProximityScreenOff,
    @ApplicationContext private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val state: StateFlow<CallUiState> = _state

    private var webRtcClient: WebRtcClient? = null
    private var durationTimerJob: Job? = null
    private var pendingRemoteVideoTrack: VideoTrack? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    // Incoming call.incoming carries the caller's display name/phone
    // (the callee needs it before any REST round trip); the caller's own
    // REST responses don't carry the *other* party's name (see
    // CallRepositoryImpl's toDomain doc comment), so it's cached here from
    // whichever call.incoming or startCall() first supplied it, to fill
    // in Ended/Active states consistently regardless of which side of
    // the call this device was on.
    private var cachedOtherDisplayName: String? = null
    private var cachedOtherPhoneE164: String = ""

    // ---- Phase 2B: reconnect, quality, in-call chat and reactions ----
    private var qualityTracker = QualityTracker()
    private var qualityJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var reactionCounter = 0L

    private val _chat = MutableStateFlow<List<CallChatLine>>(emptyList())
    val chat: StateFlow<List<CallChatLine>> = _chat
    private val _unreadChat = MutableStateFlow(0)
    val unreadChat: StateFlow<Int> = _unreadChat
    private var chatPanelOpen = false
    private val _reactions = MutableSharedFlow<CallReaction>(extraBufferCapacity = 16)
    val reactions: SharedFlow<CallReaction> = _reactions

    init {
        scope.launch {
            callRepository.signals.collect { signal -> handleSignal(signal) }
        }
        // Route changes (headset plugged in, Bluetooth gone, user picked speaker) are reflected in the call state.
        scope.launch {
            audioRouter.state.collect { routeState ->
                val active = _state.value as? CallUiState.Active ?: return@collect
                _state.value = active.copy(
                    route = routeState.selected,
                    availableRoutes = routeState.available,
                    isSpeakerOn = routeState.selected == AudioRoute.SPEAKER,
                )
                proximity.setEnabled(AudioRoutePolicy.usesProximity(routeState.selected, active.session.callType))
            }
        }
    }

    fun selectAudioRoute(route: AudioRoute) = audioRouter.select(route)

    fun sendChat(text: String) {
        val callId = currentCallId() ?: return
        val clean = text.trim().take(500)
        if (clean.isEmpty()) return
        callRepository.sendChat(callId, clean)
        _chat.value = _chat.value + CallChatLine(fromMe = true, text = clean, atMillis = System.currentTimeMillis())
    }

    fun sendReaction(emoji: String) {
        val callId = currentCallId() ?: return
        callRepository.sendReaction(callId, emoji)
        _reactions.tryEmit(CallReaction(++reactionCounter, emoji, fromMe = true))
    }

    /** The chat panel being open clears the unread dot and keeps it cleared while lines arrive. */
    fun setChatPanelOpen(open: Boolean) {
        chatPanelOpen = open
        if (open) _unreadChat.value = 0
    }

    fun startOutgoingCall(conversationId: String, callType: CallType) {
        if (_state.value !is CallUiState.Idle) return // one call at a time — spec never asked for call waiting

        scope.launch {
            try {
                val session = callRepository.startCall(conversationId, callType)
                cachedOtherDisplayName = session.otherDisplayName
                cachedOtherPhoneE164 = session.otherPhoneE164
                _state.value = CallUiState.Outgoing(session)
                setUpPeerConnection(session)
                CallForegroundService.startRinging(appContext, callType, isOutgoing = true)
            } catch (e: Exception) {
                Log.w(TAG, "startCall failed", e)
                resetToIdle()
            }
        }
    }

    fun acceptIncomingCall() {
        val incoming = _state.value as? CallUiState.Incoming ?: return
        scope.launch {
            try {
                val session = callRepository.acceptCall(incoming.callId)
                _state.value = CallUiState.Connecting(session)
                setUpPeerConnection(session)
                CallForegroundService.startActive(appContext, session.callType)
            } catch (e: Exception) {
                Log.w(TAG, "acceptCall failed", e)
                resetToIdle()
            }
        }
    }

    fun declineIncomingCall() {
        val incoming = _state.value as? CallUiState.Incoming ?: return
        scope.launch {
            runCatching { callRepository.declineCall(incoming.callId) }
            showEndedThenReset(CallStatus.DECLINED, incoming.callType)
        }
    }

    /** The user-facing hang-up action, valid from Outgoing, Connecting, or Active. */
    fun hangUp() {
        val callId = currentCallId() ?: return
        val callType = currentCallType() ?: CallType.VOICE
        scope.launch {
            callRepository.sendHangupSignal(callId) // fast local teardown for the other party
            runCatching { callRepository.endCall(callId, reason = "user_hangup") } // authoritative
            showEndedThenReset(CallStatus.ENDED, callType)
        }
    }

    fun toggleMute() {
        val active = _state.value as? CallUiState.Active ?: return
        val newMuted = !active.isMuted
        webRtcClient?.setMuted(newMuted)
        _state.value = active.copy(isMuted = newMuted)
    }

    fun toggleSpeaker() {
        val active = _state.value as? CallUiState.Active ?: return
        if (active.route == AudioRoute.SPEAKER) {
            // Back to the best non-speaker output that is available (headset > Bluetooth > earpiece).
            val fallback = AudioRoutePolicy.initial(CallType.VOICE, active.availableRoutes.filter { it != AudioRoute.SPEAKER })
            audioRouter.select(fallback)
        } else {
            audioRouter.select(AudioRoute.SPEAKER)
        }
    }

    fun switchCamera() {
        val active = _state.value as? CallUiState.Active ?: return
        if (active.session.callType != CallType.VIDEO) return
        webRtcClient?.switchCamera()
        _state.value = active.copy(isFrontCamera = !active.isFrontCamera)
    }

    fun toggleLocalVideo() {
        val active = _state.value as? CallUiState.Active ?: return
        if (active.session.callType != CallType.VIDEO) return
        val enabled = !active.isLocalVideoEnabled
        webRtcClient?.setLocalVideoEnabled(enabled)
        _state.value = active.copy(isLocalVideoEnabled = enabled)
    }

    /** Called once per renderer, from the in-call Composable's AndroidView factory — a no-op if the peer connection isn't set up yet is fine, since attachRenderersIfPending() re-applies once it is. */
    fun attachRenderers(local: SurfaceViewRenderer, remote: SurfaceViewRenderer) {
        localRenderer = local
        remoteRenderer = remote
        pendingRemoteVideoTrack?.let { remote.let { r -> it.addSink(r) } }
    }

    private fun setUpPeerConnection(session: CallSession) {
        val client = webRtcClientFactory.create(
            callType = session.callType,
            iceServers = session.iceServers,
            listener = object : WebRtcClientListener {
                override fun onLocalIceCandidate(candidate: LocalIceCandidate) {
                    callRepository.sendIceCandidate(session.id, candidate)
                }

                override fun onConnectionStateChanged(state: PeerConnection.IceConnectionState) {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED,
                        -> {
                            onConnectionRestored()
                            promoteToActive(session)
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> beginReconnect(session, failed = false)
                        PeerConnection.IceConnectionState.FAILED -> {
                            if (_state.value is CallUiState.Active) {
                                beginReconnect(session, failed = true)
                            } else {
                                // Never got connected at all: nothing to restore, end like before.
                                scope.launch {
                                    runCatching { callRepository.endCall(session.id, reason = "ice_failed") }
                                    showEndedThenReset(CallStatus.ENDED, session.callType)
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                override fun onRemoteVideoTrackAvailable(track: VideoTrack) {
                    pendingRemoteVideoTrack = track
                    remoteRenderer?.let { track.addSink(it) }
                }
            },
        )
        webRtcClient = client
        client.start(localRenderer, remoteRenderer)

        if (session.isOutgoing) {
            scope.launch {
                val offer = client.createOffer()
                callRepository.sendOffer(session.id, offer)
            }
        }
        // The callee doesn't create an answer here — it waits for the
        // caller's call.offer signal, handled in handleSignal below,
        // since accept() and the offer's arrival can race in either order.
    }

    private fun promoteToActive(session: CallSession) {
        val current = _state.value
        if (current is CallUiState.Active) return // already promoted — IceConnectionState can flip CONNECTED more than once (e.g. after a brief network hiccup)
        audioRouter.start(session.callType) // video: speaker, voice: earpiece, a headset always wins
        val routeState = audioRouter.state.value
        _state.value = CallUiState.Active(
            session = session,
            durationSeconds = 0,
            isMuted = false,
            isSpeakerOn = routeState.selected == AudioRoute.SPEAKER,
            isFrontCamera = com.omidgame.mench.core.webrtc.CallSettingsStore.current.frontCameraDefault,
            isLocalVideoEnabled = session.callType == CallType.VIDEO,
            route = routeState.selected,
            availableRoutes = routeState.available,
        )
        proximity.setEnabled(AudioRoutePolicy.usesProximity(routeState.selected, session.callType))
        startDurationTimer()
        startQualityMonitor(session)
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = scope.launch {
            while (true) {
                delay(1000)
                val active = _state.value as? CallUiState.Active ?: break
                _state.value = active.copy(durationSeconds = active.durationSeconds + 1)
            }
        }
    }

    /** The connection dropped mid-call: show "Reconnecting…", let the caller restart ICE on a schedule, give up after ~28 s. */
    private fun beginReconnect(session: CallSession, failed: Boolean) {
        if (reconnectJob?.isActive == true) return
        val active = _state.value as? CallUiState.Active ?: return
        _state.value = active.copy(reconnecting = true)
        val disconnectedAt = System.currentTimeMillis() - if (failed) ReconnectPolicy.GRACE_MS else 0L
        reconnectAttempts = 0
        reconnectJob = scope.launch {
            while (_state.value is CallUiState.Active) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - disconnectedAt
                when (ReconnectPolicy.nextAction(elapsed, reconnectAttempts, session.isOutgoing)) {
                    ReconnectPolicy.Action.WAIT -> Unit
                    ReconnectPolicy.Action.RESTART_ICE -> {
                        reconnectAttempts++
                        val client = webRtcClient ?: break
                        runCatching {
                            val offer = client.createOffer(iceRestart = true)
                            callRepository.sendOffer(session.id, offer)
                        }.onFailure { Log.w(TAG, "ICE restart failed", it) }
                    }
                    ReconnectPolicy.Action.GIVE_UP -> {
                        runCatching { callRepository.sendHangupSignal(session.id) }
                        runCatching { callRepository.endCall(session.id, reason = "connection_lost") }
                        showEndedThenReset(CallStatus.ENDED, session.callType)
                        break
                    }
                }
            }
        }
    }

    private fun onConnectionRestored() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        val active = _state.value as? CallUiState.Active ?: return
        if (active.reconnecting) _state.value = active.copy(reconnecting = false)
    }

    /** Every 3 s: read RTT / loss / jitter, debounce into GOOD / FAIR / POOR, and cap the video bitrate to match. */
    private fun startQualityMonitor(session: CallSession) {
        qualityJob?.cancel()
        qualityTracker = QualityTracker()
        qualityJob = scope.launch {
            var lastBitrateTier = CallQualityTier.UNKNOWN
            while (true) {
                delay(3000)
                val client = webRtcClient ?: break
                val sample = client.sampleStats()
                val tier = qualityTracker.push(sample)
                val active = _state.value as? CallUiState.Active ?: break
                if (active.quality != tier) _state.value = active.copy(quality = tier)
                if (session.callType == CallType.VIDEO && tier != CallQualityTier.UNKNOWN && tier != lastBitrateTier) {
                    val preset = com.omidgame.mench.core.webrtc.CallSettingsStore.current.qualityPreset
                    client.setMaxVideoBitrate(CallQuality.maxVideoBitrateBps(tier, preset))
                    lastBitrateTier = tier
                }
                // Settings Phase 2: remember the last measurement for the call diagnostics page (numbers only).
                com.omidgame.mench.core.webrtc.CallDiagnostics.last = com.omidgame.mench.core.webrtc.CallDiagnosticsSnapshot(
                    callType = session.callType.name,
                    rttMs = sample.rttMs,
                    packetLossPercent = sample.packetLossPercent,
                    jitterMs = sample.jitterMs,
                    tier = tier.name,
                    bitrateCapBps = if (session.callType == CallType.VIDEO) CallQuality.maxVideoBitrateBps(tier, com.omidgame.mench.core.webrtc.CallSettingsStore.current.qualityPreset) else null,
                    preset = com.omidgame.mench.core.webrtc.CallSettingsStore.current.qualityPreset.name,
                    recordedAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun stopCallExtras() {
        reconnectJob?.cancel()
        reconnectJob = null
        qualityJob?.cancel()
        qualityJob = null
        reconnectAttempts = 0
        audioRouter.stop()
        proximity.setEnabled(false)
    }

    private suspend fun handleSignal(signal: CallSignalEvent) {
        when (signal) {
            is CallSignalEvent.Incoming -> {
                if (_state.value !is CallUiState.Idle) return // already on a call — silently ignore rather than interrupt; spec never asked for call waiting
                cachedOtherDisplayName = signal.callerDisplayName
                cachedOtherPhoneE164 = signal.callerPhoneE164
                _state.value = CallUiState.Incoming(
                    callId = signal.callId,
                    conversationId = signal.conversationId,
                    callType = signal.callType,
                    callerDisplayName = signal.callerDisplayName,
                    callerPhoneE164 = signal.callerPhoneE164,
                )
                CallForegroundService.startRinging(appContext, signal.callType, isOutgoing = false)
            }
            is CallSignalEvent.Accepted -> {
                // Caller side: callee accepted, still negotiating ICE — the
                // Outgoing -> Connecting move itself is cosmetic, the real
                // work (createOffer/sendOffer) already happened in
                // setUpPeerConnection when the call was started.
                (_state.value as? CallUiState.Outgoing)?.let { _state.value = CallUiState.Connecting(it.session) }
            }
            is CallSignalEvent.Declined -> showEndedThenReset(CallStatus.DECLINED, currentCallType() ?: CallType.VOICE)
            is CallSignalEvent.Ended -> showEndedThenReset(signal.status, currentCallType() ?: CallType.VOICE)
            is CallSignalEvent.RemoteHangup -> showEndedThenReset(CallStatus.ENDED, currentCallType() ?: CallType.VOICE)
            is CallSignalEvent.RemoteOffer -> {
                val client = webRtcClient ?: return
                client.setRemoteOffer(signal.sdp)
                val answer = client.createAnswer()
                callRepository.sendAnswer(signal.callId, answer)
            }
            is CallSignalEvent.RemoteAnswer -> webRtcClient?.setRemoteAnswer(signal.sdp)
            is CallSignalEvent.RemoteIceCandidate -> webRtcClient?.addRemoteIceCandidate(
                LocalIceCandidate(signal.sdpMid, signal.sdpMLineIndex ?: 0, signal.candidate),
            )
            is CallSignalEvent.RemoteChat -> if (signal.callId == currentCallId()) {
                _chat.value = _chat.value + CallChatLine(fromMe = false, text = signal.text, atMillis = System.currentTimeMillis())
                if (!chatPanelOpen) _unreadChat.value = _unreadChat.value + 1
            }
            is CallSignalEvent.RemoteReaction -> if (signal.callId == currentCallId()) {
                _reactions.tryEmit(CallReaction(++reactionCounter, signal.emoji, fromMe = false))
            }
        }
    }

    private fun showEndedThenReset(status: CallStatus, callType: CallType) {
        durationTimerJob?.cancel()
        stopCallExtras()
        webRtcClient?.dispose()
        webRtcClient = null
        pendingRemoteVideoTrack = null
        CallForegroundService.stop(appContext)
        _state.value = CallUiState.Ended(status, callType, cachedOtherDisplayName, cachedOtherPhoneE164)
        scope.launch {
            delay(2500)
            if (_state.value is CallUiState.Ended) resetToIdle()
        }
    }

    private fun resetToIdle() {
        durationTimerJob?.cancel()
        stopCallExtras()
        _chat.value = emptyList()
        _unreadChat.value = 0
        chatPanelOpen = false
        webRtcClient?.dispose()
        webRtcClient = null
        pendingRemoteVideoTrack = null
        localRenderer = null
        remoteRenderer = null
        cachedOtherDisplayName = null
        cachedOtherPhoneE164 = ""
        CallForegroundService.stop(appContext)
        _state.value = CallUiState.Idle
    }

    private fun currentCallId(): String? = when (val s = _state.value) {
        is CallUiState.Outgoing -> s.session.id
        is CallUiState.Connecting -> s.session.id
        is CallUiState.Active -> s.session.id
        is CallUiState.Incoming -> s.callId
        else -> null
    }

    private fun currentCallType(): CallType? = when (val s = _state.value) {
        is CallUiState.Outgoing -> s.session.callType
        is CallUiState.Connecting -> s.session.callType
        is CallUiState.Active -> s.session.callType
        is CallUiState.Incoming -> s.callType
        else -> null
    }

    private companion object {
        const val TAG = "CallSessionManager"
    }
}
