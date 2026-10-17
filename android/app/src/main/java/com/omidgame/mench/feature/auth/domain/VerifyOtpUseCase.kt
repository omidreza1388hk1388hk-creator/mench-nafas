package com.omidgame.mench.feature.auth.domain

import javax.inject.Inject

class VerifyOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        challenge: OtpChallenge,
        code: String,
        deviceName: String,
    ): AuthResult<Unit> {
        val cleanCode = normalizeOtpCode(code)
        if (cleanCode.isEmpty()) {
            return AuthResult.Failure(AuthFailureReason.INVALID_OR_EXPIRED_CODE)
        }
        return authRepository.verifyOtp(challenge, cleanCode, deviceName)
    }
}
