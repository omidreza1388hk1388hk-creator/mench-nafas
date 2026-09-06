package com.omidgame.mench.feature.auth.domain

/** Domain-level result type — the presentation layer never sees raw exceptions or HTTP codes. */
sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val reason: AuthFailureReason) : AuthResult<Nothing>
}

enum class AuthFailureReason {
    NETWORK_UNAVAILABLE,
    INVALID_OR_EXPIRED_CODE,
    RATE_LIMITED,
    UNKNOWN,
}

data class OtpChallenge(val challengeId: String, val phone: String)
