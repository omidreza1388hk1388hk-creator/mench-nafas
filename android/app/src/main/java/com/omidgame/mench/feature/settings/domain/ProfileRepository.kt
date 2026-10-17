package com.omidgame.mench.feature.settings.domain

data class Profile(
    val id: String = "",
    val avatarVersion: Long? = null,
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

    /** Uploads a new profile photo (downscaled + JPEG-compressed on the device first). Returns the refreshed profile, or null on failure. */
    suspend fun uploadAvatar(imageUri: String): Profile?

    suspend fun updateProfile(
        displayName: String?,
        username: String?,
        bio: String?,
    ): ProfileUpdateResult
}
