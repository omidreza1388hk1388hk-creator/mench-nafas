package com.omidgame.mench.feature.calls.domain

sealed interface CallUiState {
    object Idle : CallUiState

    data class Outgoing(val session: CallSession) : CallUiState

    data class Incoming(
        val callId: String,
        val conversationId: String,
        val callType: CallType,
        val callerDisplayName: String?,
        val callerPhoneE164: String,
    ) : CallUiState

    /** Accepted server-side, PeerConnection negotiating — shown between "Incoming"/"Outgoing" and "Active" so the UI never has a silent gap while ICE connects. */
    data class Connecting(val session: CallSession) : CallUiState

    data class Active(
        val session: CallSession,
        val durationSeconds: Int,
        val isMuted: Boolean,
        val isSpeakerOn: Boolean,
        val isFrontCamera: Boolean,
        val isLocalVideoEnabled: Boolean,
    ) : CallUiState

    /** Transient — CallSessionManager shows this for a couple of seconds so the ending reason is visible, then resets to Idle on its own. */
    data class Ended(val status: CallStatus, val callType: CallType, val otherDisplayName: String?, val otherPhoneE164: String) : CallUiState
}
