package com.omidgame.mench.feature.auth.presentation

import com.omidgame.mench.feature.auth.domain.AuthFailureReason
import com.omidgame.mench.feature.auth.domain.OtpChallenge
import com.omidgame.mench.feature.auth.domain.TwoStepChallenge

/** Every screen has an explicit state for each of: loading, success, empty, error (spec section 66). */
sealed interface AuthUiState {
    data object PhoneEntry : AuthUiState
    data object RequestingOtp : AuthUiState
    data class OtpEntry(
        val challenge: OtpChallenge,
        val isVerifying: Boolean = false,
        val errorReason: AuthFailureReason? = null,
        val resendCooldownSeconds: Int = 0,
    ) : AuthUiState
    /** Account has two-step verification: the SMS code was accepted, the password is next. */
    data class TwoStepEntry(
        val challenge: TwoStepChallenge,
        val isVerifying: Boolean = false,
        val errorReason: AuthFailureReason? = null,
    ) : AuthUiState
    data object SignedIn : AuthUiState
    data class Error(val reason: AuthFailureReason) : AuthUiState
}
