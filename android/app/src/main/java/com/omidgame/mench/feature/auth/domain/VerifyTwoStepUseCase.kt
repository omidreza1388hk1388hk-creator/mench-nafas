package com.omidgame.mench.feature.auth.domain

import javax.inject.Inject

class VerifyTwoStepUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(challenge: TwoStepChallenge, password: String): AuthResult<Unit> {
        if (password.isEmpty()) return AuthResult.Failure(AuthFailureReason.WRONG_PASSWORD)
        return authRepository.verifyTwoStep(challenge, password)
    }
}
