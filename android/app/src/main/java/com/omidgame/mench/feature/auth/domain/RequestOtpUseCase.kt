package com.omidgame.mench.feature.auth.domain

import javax.inject.Inject

class RequestOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(phoneE164: String): AuthResult<OtpChallenge> {
        // Minimal client-side shape validation only — the server is the
        // authority on what counts as a valid phone number (spec 34: never
        // trust client-side validation alone).
        if (!phoneE164.startsWith("+") || phoneE164.length < 8) {
            return AuthResult.Failure(AuthFailureReason.UNKNOWN)
        }
        return authRepository.requestOtp(phoneE164)
    }
}
