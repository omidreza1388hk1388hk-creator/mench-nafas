package com.omidgame.mench.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class InitiateCallBody(val conversationId: String, val callType: String)

data class EndCallBody(val reason: String? = null)

data class IceServerResponse(val urls: List<String>, val username: String?, val credential: String?)

data class CallSessionResponse(
    val id: String,
    val conversationId: String,
    val callerId: String,
    val calleeId: String,
    val callType: String,
    val status: String,
    val startedAt: String,
    val answeredAt: String?,
    val endedAt: String?,
    val endReason: String?,
    val iceServers: List<IceServerResponse>,
)

data class CallHistorySummaryResponse(
    val id: String,
    val conversationId: String,
    val callType: String,
    val status: String,
    val startedAt: String,
    val answeredAt: String?,
    val endedAt: String?,
    val durationSeconds: Int?,
    val wasOutgoing: Boolean,
    val otherUserId: String,
    val otherUserPhoneE164: String,
    val otherUserDisplayName: String?,
)

/** Built on the @AuthenticatedClient OkHttpClient, same as ChatApi — every call route requires the bearer access token. */
interface CallsApi {
    @POST("calls")
    suspend fun initiate(@Body body: InitiateCallBody): CallSessionResponse

    @GET("calls/{callId}")
    suspend fun get(@Path("callId") callId: String): CallSessionResponse

    @POST("calls/{callId}/accept")
    suspend fun accept(@Path("callId") callId: String): CallSessionResponse

    @POST("calls/{callId}/decline")
    suspend fun decline(@Path("callId") callId: String)

    @POST("calls/{callId}/end")
    suspend fun end(@Path("callId") callId: String, @Body body: EndCallBody)

    @GET("calls")
    suspend fun listHistory(
        @Query("before") before: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<CallHistorySummaryResponse>
}
