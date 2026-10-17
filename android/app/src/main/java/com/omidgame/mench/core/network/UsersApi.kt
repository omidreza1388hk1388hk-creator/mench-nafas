package com.omidgame.mench.core.network

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

data class UserProfileResponse(
    val id: String,
    val phoneE164: String,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String?,
    val bio: String?,
    /** Profile-photo version (ms timestamp); null = no photo. Default keeps older servers parseable. */
    val avatarVersion: Long? = null,
)

data class UpdateProfileBody(
    val displayName: String? = null,
    val username: String? = null,
    val bio: String? = null,
)

data class LookupUsersBatchBody(val phones: List<String>)

data class ContactPrivacyResponse(val discoverable: Boolean)

data class ContactPrivacyBody(val discoverable: Boolean)

interface UsersApi {
    @GET("users/me")
    suspend fun getMe(): UserProfileResponse

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UserProfileResponse

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateProfileBody): UserProfileResponse

    /** Contact sync — see UsersController.lookupBatch on the backend for the throttling/matching semantics (silently omits non-matches, never 404s). */
    /** Phase 1 completion: whether people with my number in their contacts can find me via contact sync. */
    @GET("users/me/contact-privacy")
    suspend fun getContactPrivacy(): ContactPrivacyResponse

    @PATCH("users/me/contact-privacy")
    suspend fun updateContactPrivacy(@Body body: ContactPrivacyBody): ContactPrivacyResponse

    @POST("users/lookup-batch")
    suspend fun lookupBatch(@Body body: LookupUsersBatchBody): List<UserProfileResponse>
}
