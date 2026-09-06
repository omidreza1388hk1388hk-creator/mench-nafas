package com.omidgame.mench.feature.calls.presentation

import androidx.lifecycle.ViewModel
import com.omidgame.mench.feature.calls.domain.CallSessionManager
import com.omidgame.mench.feature.calls.domain.CallType
import com.omidgame.mench.feature.calls.domain.CallUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Deliberately thin — CallSessionManager (a @Singleton, not a
 * ViewModel) is where all the actual state and orchestration lives, so
 * that state survives regardless of which screen's hiltViewModel()
 * created this particular instance. See MenchNavGraph for how the same
 * underlying manager backs both the global call overlay and ChatScreen's
 * call buttons.
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    private val sessionManager: CallSessionManager,
) : ViewModel() {

    val uiState: StateFlow<CallUiState> = sessionManager.state

    fun startCall(conversationId: String, callType: CallType) = sessionManager.startOutgoingCall(conversationId, callType)
    fun accept() = sessionManager.acceptIncomingCall()
    fun decline() = sessionManager.declineIncomingCall()
    fun hangUp() = sessionManager.hangUp()
    fun toggleMute() = sessionManager.toggleMute()
    fun toggleSpeaker() = sessionManager.toggleSpeaker()
    fun switchCamera() = sessionManager.switchCamera()
    fun toggleLocalVideo() = sessionManager.toggleLocalVideo()
    fun attachRenderers(local: org.webrtc.SurfaceViewRenderer, remote: org.webrtc.SurfaceViewRenderer) =
        sessionManager.attachRenderers(local, remote)
}
