package com.omidgame.mench.feature.calls.domain

import android.content.Context
import android.util.Log
import com.omidgame.mench.core.webrtc.WebRtcClient
import com.omidgame.mench.core.webrtc.WebRtcClientFactory
import com.omidgame.mench.core.webrtc.WebRtcClientListener
import com.omidgame.mench.feature.calls.service.CallForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

    init {
        scope.launch {
            callRepository.signals.collect { signal -> handleSignal(signal) }
        }
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
        val newSpeakerOn = !active.isSpeakerOn
        webRtcClient?.setSpeakerphoneOn(newSpeakerOn)
        _state.value = active.copy(isSpeakerOn = newSpeakerOn)
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
                    if (state == PeerConnection.IceConnectionState.CONNECTED) {
                        promoteToActive(session)
                    } else if (state == PeerConnection.IceConnectionState.FAILED) {
                        scope.launch {
                            runCatching { callRepository.endCall(session.id, reason = "ice_failed") }
                            showEndedThenReset(CallStatus.ENDED, session.callType)
                        }
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
        _state.value = CallUiState.Active(
            session = session,
            durationSeconds = 0,
            isMuted = false,
            isSpeakerOn = session.callType == CallType.VIDEO, // speaker by default for video, earpiece for voice — matches how people actually hold the phone for each
            isFrontCamera = true,
            isLocalVideoEnabled = session.callType == CallType.VIDEO,
        )
        webRtcClient?.setSpeakerphoneOn(session.callType == CallType.VIDEO)
        startDurationTimer()
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
        }
    }

    private fun showEndedThenReset(status: CallStatus, callType: CallType) {
        durationTimerJob?.cancel()
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
