package com.omidgame.mench.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

data class CreateDirectConversationBody(val targetUserId: String)

data class CreateGroupBody(val title: String, val memberIds: List<String>)

data class AddMembersBody(val memberIds: List<String>)

data class RenameGroupBody(val title: String)

data class ConversationMemberResponse(
    val userId: String,
    val phoneE164: String,
    val displayName: String?,
    val role: String,
)

data class ConversationResponse(
    val id: String,
    val kind: String,
    val title: String?,
    val createdBy: String?,
    val createdAt: String,
    val lastMessageAt: String?,
    val otherUserId: String?,
    val otherUserPhoneE164: String?,
    val otherUserDisplayName: String?,
)

data class SendMessageBody(
    val clientMsgId: String,
    val body: String? = null,
    val attachmentId: String? = null,
)

data class MessageAttachmentSummaryResponse(
    val id: String,
    val kind: String,
    val mimeType: String,
    val originalFilename: String,
    val sizeBytes: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val durationMs: Long?,
    val hasThumbnail: Boolean,
)

data class ReactionSummaryResponse(
    val emoji: String,
    val userIds: List<String>,
)

data class MessageResponse(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val clientMsgId: String,
    val kind: String,
    val body: String?,
    val attachment: MessageAttachmentSummaryResponse?,
    val sequence: Long,
    val createdAt: String,
    val editedAt: String?,
    val deletedAt: String?,
    val forwardedFromMessageId: String?,
    val reactions: List<ReactionSummaryResponse> = emptyList(),
)

data class MarkReadBody(val lastReadSequence: Long)

data class EditMessageBody(val body: String)

data class ReactMessageBody(val emoji: String)

data class ForwardMessageBody(val sourceMessageId: String, val clientMsgId: String)

data class UserLookupResponse(
    val id: String,
    val phoneE164: String,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String?,
)

data class AttachmentResponse(
    val id: String,
    val conversationId: String,
    val uploaderId: String,
    val kind: String,
    val originalFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val durationMs: Long?,
    val hasThumbnail: Boolean,
    val createdAt: String,
)

data class RegisterPushTokenBody(val token: String, val provider: String = "fcm")

data class NotificationPrivacyResponse(val mode: String)

data class UpdateNotificationPrivacyBody(val mode: String)

data class MessageSearchResultResponse(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val kind: String,
    val body: String?,
    val sequence: Long,
    val createdAt: String,
    val conversationTitle: String?,
    val conversationOtherUserDisplayName: String?,
)

data class ConversationSearchResultResponse(
    val id: String,
    val kind: String,
    val title: String?,
    val lastMessageAt: String?,
    val otherUserId: String?,
    val otherUserPhoneE164: String?,
    val otherUserDisplayName: String?,
)

data class FileSearchResultResponse(
    val id: String,
    val conversationId: String,
    val uploaderId: String,
    val kind: String,
    val originalFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val durationMs: Long?,
    val hasThumbnail: Boolean,
    val createdAt: String,
)

data class SearchResultsResponse(
    val messages: List<MessageSearchResultResponse>,
    val conversations: List<ConversationSearchResultResponse>,
    val files: List<FileSearchResultResponse>,
)

data class DiagnosticCheckResponse(
    val name: String,
    val status: String, // "pass" | "warning" | "fail"
    val detail: String,
    val latencyMs: Long,
)

data class DiagnosticsReportResponse(
    val overall: String,
    val serverTime: String,
    val checks: List<DiagnosticCheckResponse>,
)

/**
 * All routes here require the bearer access token, so ChatApi is built on
 * the @AuthenticatedClient OkHttpClient (see NetworkModule) — unlike
 * AuthApi, which intentionally uses the unauthenticated one.
 */
interface ChatApi {
    @POST("conversations/direct")
    suspend fun createDirectConversation(@Body body: CreateDirectConversationBody): ConversationResponse

    @POST("conversations/group")
    suspend fun createGroup(@Body body: CreateGroupBody): ConversationResponse

    @GET("conversations")
    suspend fun listConversations(): List<ConversationResponse>

    @GET("conversations/{conversationId}/members")
    suspend fun listMembers(@Path("conversationId") conversationId: String): List<ConversationMemberResponse>

    @POST("conversations/{conversationId}/members")
    suspend fun addMembers(@Path("conversationId") conversationId: String, @Body body: AddMembersBody)

    @DELETE("conversations/{conversationId}/members/{userId}")
    suspend fun removeMember(@Path("conversationId") conversationId: String, @Path("userId") userId: String)

    @PATCH("conversations/{conversationId}")
    suspend fun renameGroup(@Path("conversationId") conversationId: String, @Body body: RenameGroupBody)

    @POST("conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Body body: SendMessageBody,
    ): MessageResponse

    @GET("conversations/{conversationId}/messages")
    suspend fun listMessages(
        @Path("conversationId") conversationId: String,
        @Query("after") after: Long? = null,
        @Query("limit") limit: Int? = null,
    ): List<MessageResponse>

    @POST("conversations/{conversationId}/read")
    suspend fun markRead(
        @Path("conversationId") conversationId: String,
        @Body body: MarkReadBody,
    )

    @GET("users/lookup")
    suspend fun lookupUserByPhone(@Query("phone") phone: String): UserLookupResponse

    @PATCH("conversations/{conversationId}/messages/{messageId}")
    suspend fun editMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
        @Body body: EditMessageBody,
    ): MessageResponse

    @DELETE("conversations/{conversationId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
    )

    @POST("conversations/{conversationId}/messages/{messageId}/reactions")
    suspend fun setReaction(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
        @Body body: ReactMessageBody,
    ): List<ReactionSummaryResponse>

    @DELETE("conversations/{conversationId}/messages/{messageId}/reactions")
    suspend fun clearReaction(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
    ): List<ReactionSummaryResponse>

    @POST("conversations/{conversationId}/messages/forward")
    suspend fun forwardMessage(
        @Path("conversationId") conversationId: String,
        @Body body: ForwardMessageBody,
    ): MessageResponse

    @Multipart
    @POST("conversations/{conversationId}/attachments")
    suspend fun uploadAttachment(
        @Path("conversationId") conversationId: String,
        @Part file: MultipartBody.Part,
        @Part("durationMs") durationMs: RequestBody? = null,
        @Part thumbnail: MultipartBody.Part? = null,
        @Part("widthPx") widthPx: RequestBody? = null,
        @Part("heightPx") heightPx: RequestBody? = null,
    ): AttachmentResponse

    // --- Phase 6: push notifications ---

    @PUT("devices/{deviceId}/push-token")
    suspend fun registerPushToken(@Path("deviceId") deviceId: String, @Body body: RegisterPushTokenBody)

    @GET("users/me/notification-privacy")
    suspend fun getNotificationPrivacy(): NotificationPrivacyResponse

    @PATCH("users/me/notification-privacy")
    suspend fun updateNotificationPrivacy(@Body body: UpdateNotificationPrivacyBody)

    // --- Phase 6: universal search ---

    /**
     * scope: "all" | "messages" | "conversations" | "files". q may be
     * omitted only when scope="files" (server-side "browse all files" —
     * see SearchRepository.searchFiles on the backend).
     */
    @GET("search")
    suspend fun search(
        @Query("q") query: String?,
        @Query("scope") scope: String? = null,
        @Query("conversationId") conversationId: String? = null,
        @Query("limit") limit: Int? = null,
    ): SearchResultsResponse

    @GET("diagnostics/full")
    suspend fun runDiagnostics(): DiagnosticsReportResponse
}
