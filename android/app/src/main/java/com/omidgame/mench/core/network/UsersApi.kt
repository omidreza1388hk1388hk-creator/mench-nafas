package com.omidgame.mench.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

data class UserProfileResponse(
    val id: String,
    val phoneE164: String,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String?,
    val bio: String?,
)

data class UpdateProfileBody(
    val displayName: String? = null,
    val username: String? = null,
    val bio: String? = null,
)

interface UsersApi {
    @GET("users/me")
    suspend fun getMe(): UserProfileResponse

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateProfileBody): UserProfileResponse
}
