package com.omidgame.mench.core.network

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class PublicUserResponse(
    val id: String,
    val displayName: String?,
    val username: String?,
    val bio: String?,
    val avatarVersion: Long? = null,
    val phoneE164: String,
)

data class BlockedUserResponse(
    val id: String,
    val displayName: String?,
    val username: String?,
    val bio: String?,
    val avatarVersion: Long? = null,
    val phoneE164: String,
    val blockedAt: String,
)

data class InviteResponse(val code: String, val link: String, val deepLink: String)
data class BlockBody(val userId: String)
data class DeleteAccountBody(val confirm: String)
data class FeedbackBody(
    val category: String,
    val message: String,
    val appVersion: String?,
    val deviceInfo: String?,
    val logs: String?,
)
data class EventBody(val name: String)

data class AnnouncementWire(val id: String, val title: String, val body: String, val level: String)
data class UpdateInfoWire(val latestVersionCode: Long = 0, val minVersionCode: Long = 0, val downloadUrl: String? = null)
data class RemoteConfigResponse(
    val flags: Map<String, Boolean> = emptyMap(),
    val announcements: List<AnnouncementWire> = emptyList(),
    val update: UpdateInfoWire = UpdateInfoWire(),
)

/** Phase 1C endpoints (backend: modules/account). */
interface ProductApi {
    @GET("account/invite") suspend fun myInvite(): InviteResponse
    @GET("account/invite/{code}") suspend fun resolveInvite(@Path("code") code: String): PublicUserResponse
    @GET("account/by-username/{username}") suspend fun byUsername(@Path("username") username: String): PublicUserResponse

    @GET("account/blocks") suspend fun blocked(): List<BlockedUserResponse>
    @POST("account/blocks") suspend fun block(@Body body: BlockBody)
    @DELETE("account/blocks/{userId}") suspend fun unblock(@Path("userId") userId: String)

    @GET("account/export") suspend fun exportData(): ResponseBody
    @POST("account/delete") suspend fun deleteAccount(@Body body: DeleteAccountBody)

    @POST("feedback") suspend fun feedback(@Body body: FeedbackBody)
    @GET("config") suspend fun config(): RemoteConfigResponse
    @POST("analytics/event") suspend fun event(@Body body: EventBody)
}
