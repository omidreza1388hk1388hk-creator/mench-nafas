package com.omidgame.mench.feature.auth.domain

/** Domain-level result type — the presentation layer never sees raw exceptions or HTTP codes. */
sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val reason: AuthFailureReason) : AuthResult<Nothing>

    /** The SMS code was right, but the account has a password: no session yet, ask for it. */
    data class TwoStepRequired(val challenge: TwoStepChallenge) : AuthResult<Nothing>
}

enum class AuthFailureReason {
    NETWORK_UNAVAILABLE,
    INVALID_OR_EXPIRED_CODE,
    RATE_LIMITED,
    WRONG_PASSWORD,
    /** The server rejected the phone number format (HTTP 400). */
    INVALID_PHONE,
    UNKNOWN,
}

data class OtpChallenge(val challengeId: String, val phone: String)

/** Ticket proving the SMS code was accepted; valid for a few minutes on the server. */
data class TwoStepChallenge(val ticket: String, val hint: String?, val phone: String)
