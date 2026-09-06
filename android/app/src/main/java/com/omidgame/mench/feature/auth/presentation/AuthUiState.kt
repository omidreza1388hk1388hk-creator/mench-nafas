package com.omidgame.mench.feature.auth.presentation

import com.omidgame.mench.feature.auth.domain.OtpChallenge

/** Every screen has an explicit state for each of: loading, success, empty, error (spec section 66). */
sealed interface AuthUiState {
    data object PhoneEntry : AuthUiState
    data object RequestingOtp : AuthUiState
    data class OtpEntry(
        val challenge: OtpChallenge,
        val isVerifying: Boolean = false,
        val errorMessage: String? = null,
        val resendCooldownSeconds: Int = 0,
    ) : AuthUiState
    data object SignedIn : AuthUiState
    data class Error(val message: String) : AuthUiState
}
