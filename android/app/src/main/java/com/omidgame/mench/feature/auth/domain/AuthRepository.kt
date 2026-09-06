package com.omidgame.mench.feature.auth.domain

/**
 * Domain-facing contract for authentication. The data-layer implementation
 * is the only piece that knows about Retrofit/Room/EncryptedSharedPreferences;
 * everything above this interface (use cases, ViewModel) depends only on
 * these method signatures, so Phase 2+ can change transport details
 * without touching presentation code.
 */
interface AuthRepository {
    suspend fun requestOtp(phoneE164: String): AuthResult<OtpChallenge>

    suspend fun verifyOtp(
        challenge: OtpChallenge,
        code: String,
        deviceName: String,
    ): AuthResult<Unit>

    suspend fun isSignedIn(): Boolean

    suspend fun logout()

    /**
     * Revokes every session for this account, including the one making the
     * call — the backend's logoutAllDevices doesn't exempt the caller (see
     * SessionsRepository), so this always ends with the local device
     * signed out too, same as [logout]. It exists as a distinct action
     * because it also revokes every *other* device in one step, which
     * plain [logout] does not.
     */
    suspend fun logoutAllDevices()
}
