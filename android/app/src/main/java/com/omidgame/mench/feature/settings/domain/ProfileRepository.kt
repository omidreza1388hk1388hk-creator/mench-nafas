package com.omidgame.mench.feature.settings.domain

data class Profile(
    val phoneE164: String,
    val displayName: String?,
    val username: String?,
    val bio: String?,
)

enum class ProfileUpdateFailureReason {
    NETWORK_UNAVAILABLE,
    USERNAME_TAKEN,
    UNKNOWN,
}

sealed interface ProfileUpdateResult {
    data class Success(val profile: Profile) : ProfileUpdateResult
    data class Failure(val reason: ProfileUpdateFailureReason) : ProfileUpdateResult
}

interface ProfileRepository {
    suspend fun getProfile(): Profile?

    suspend fun updateProfile(
        displayName: String?,
        username: String?,
        bio: String?,
    ): ProfileUpdateResult
}
