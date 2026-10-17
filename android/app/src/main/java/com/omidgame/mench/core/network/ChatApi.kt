package com.omidgame.mench.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
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

/** Phase 8 — only non-null fields are sent (Moshi omits nulls), matching the backend's partial update. */
/** Phase 11 — server-built link preview; every field may be null when the page had nothing usable. */
data class LinkPreviewWire(val url: String, val title: String?, val description: String?, val siteName: String?)

/** Phase 11 — today's question. [partnerAnswer] is only filled once YOU have answered. */
data class QuestionResponse(
    val date: String,
    val question: String,
    val myAnswer: String?,
    val partnerAnswered: Boolean,
    val partnerAnswer: String?,
    /** Phase 3A — consecutive days both of you answered (alive through yesterday) and the best run ever. Absent on an older server. */
    val streak: Int = 0,
    val bestStreak: Int = 0,
)

data class AnswerBody(val answer: String)

data class WishWire(
    val id: String,
    val title: String,
    val done: Boolean,
    val doneAt: String?,
    val createdAt: String,
    val isMine: Boolean,
)

data class CreateWishBody(val title: String)

data class UpdateWishBody(val done: Boolean)

/** messageId = null (omitted by Moshi) clears the pin. */
data class PinBody(val messageId: String? = null)

/** Phase 9 — one person's mood. [setAt] is ISO-8601; the client only shows it for the day it was set. */
data class MoodWire(val emoji: String, val text: String?, val setAt: String)

data class MoodsResponse(val mine: MoodWire?, val other: MoodWire?)

data class UpdateMoodBody(val emoji: String, val text: String?)

/** Phase 3 — a photo in the shared album. */
data class AlbumItemWire(
    val id: String,
    val attachmentId: String,
    val caption: String,
    val isMine: Boolean,
    val myReaction: String?,
    val otherReaction: String?,
)

data class CreateAlbumBody(val attachmentId: String, val caption: String?)

/** Moshi omits a null [emoji], which the server reads as "remove my reaction". */
data class ReactAlbumBody(val emoji: String?)

/** Phase 3B — a conversation with stories you have not seen yet. */
data class UnseenStoriesWire(val conversationId: String, val count: Int)

/** Phase 3A — a "how well do you know me" quiz in the list. */
data class QuizSummaryWire(
    val id: String,
    val title: String,
    val isMine: Boolean,
    val questionCount: Int,
    val myScore: Int?,
    val myTotal: Int?,
    val partnerScore: Int?,
    val partnerTotal: Int?,
)

data class QuizQuestionWire(val q: String, val options: List<String>, val correct: Int?)

data class QuizDetailWire(val id: String, val title: String, val isMine: Boolean, val answered: Boolean, val questions: List<QuizQuestionWire>)

data class CreateQuizBody(val title: String, val questions: List<QuizQuestionWire>)

data class AnswerQuizBody(val answers: List<Int>)

data class QuizResultWire(val score: Int, val total: Int, val correct: List<Int>)

/** Phase 3 — an encrypted vault note. [blob] is opaque to the server ("v1:" + Base64); only the phones can read it. */
data class VaultItemWire(val id: String, val conversationId: String, val blob: String, val isMine: Boolean)

data class CreateVaultItemBody(val blob: String)

/** Phase 3B — a 24-hour text story. [palette] indexes the client's background gradients; [views] is only meaningful for your own stories. */
data class StoryWire(
    val id: String,
    val conversationId: String,
    val body: String,
    val emoji: String,
    val palette: Int,
    val isMine: Boolean,
    val seen: Boolean,
    val views: Int,
    val attachmentId: String? = null,
)

data class CreateStoryBody(val body: String, val emoji: String?, val palette: Int, val attachmentId: String? = null)

/** Phase 3A — one stroke on the shared canvas. [points] is a flat x0,y0,x1,y1… list, each value 0..1 of the canvas side; [color] is "#rrggbb". */
data class StrokeWire(val color: String, val width: Float, val points: List<Float>, val by: String? = null)

data class CanvasWire(val strokes: List<StrokeWire>)

data class AppendStrokesBody(val strokes: List<StrokeWire>)

/** Phase 3A — a shared counter. kind = "date" (days since/until [anchorDate]) or "tally" ([tallyCount] both people can bump). */
data class CounterWire(
    val id: String,
    val conversationId: String,
    val title: String,
    val emoji: String,
    val kind: String,
    val anchorDate: String?,
    val tallyCount: Int,
    /** Card gradient 0..5 (0 = plain). Absent on an older server. */
    val theme: Int = 0,
    val isMine: Boolean,
)

data class CreateCounterBody(val title: String, val emoji: String?, val kind: String, val anchorDate: String?, val theme: Int = 0)

data class BumpCounterBody(val delta: Int)

/** Phase 9 — a shared memory (timeline entry). [attachmentId] is an optional photo, served by the normal attachment endpoints. */
data class MemoryWire(
    val id: String,
    val conversationId: String,
    val authorId: String,
    val title: String,
    val note: String?,
    val memoryDate: String,
    val attachmentId: String?,
    val createdAt: String,
    val isMine: Boolean,
)

data class CreateMemoryBody(
    val title: String,
    val note: String?,
    val memoryDate: String,
    val attachmentId: String?,
)

data class AffectionBody(val kind: String)

/** Phase 3A — how long the heart was held (the server clamps 1000..10000 ms). */
data class HeartbeatBody(val durationMs: Long)

/** Phase 3A — [delivered] is true when the partner had a live connection to feel the pulse. */
data class HeartbeatResponse(val delivered: Boolean)

/** Phase 3A — a hand-picked full-screen effect; [effect] is MessageEffect.wire. */
data class ScreenEffectBody(val effect: String)

/** Phase 3: Together mode event; kind = start | sync | stop. */
/** Phase 4C: E2EE public key directory. */
data class E2eeKeyBody(val publicKey: String)

data class E2eeKeyResponse(val publicKey: String?)

data class TogetherBody(val kind: String, val attachmentId: String?, val positionMs: Long, val playing: Boolean)

/** Phase 9 — an "open when…" letter. [body] is null while sealed (recipient side). */
data class LetterWire(
    val id: String,
    val conversationId: String,
    val authorId: String,
    val title: String,
    val body: String?,
    val unlockAt: String?,
    val openedAt: String?,
    val createdAt: String,
    val isMine: Boolean,
    val canOpen: Boolean,
    val attachmentId: String? = null,
)

data class CreateLetterBody(val title: String, val body: String, val unlockAt: String? = null, val attachmentId: String? = null)

/** Phase 9 — Moshi omits nulls by default, so this client can only SET dates, not clear them; clearing isn't offered in the UI. */
data class UpdateUsBody(val togetherSince: String? = null, val nextMeetDate: String? = null)

data class UpdateConversationSettingsBody(
    val isMuted: Boolean? = null,
    val isPinned: Boolean? = null,
    val isArchived: Boolean? = null,
)

data class ConversationMemberResponse(
    val userId: String,
    val phoneE164: String,
    val displayName: String?,
    val role: String,
    val avatarVersion: Long? = null,
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
    /** Phase 8 — per-user settings; default false so an older server response still parses. */
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    /** Phase 11 — server id of the pinned message, null = none. */
    val pinnedMessageId: String? = null,
    /** Phase 9 — shared 'us' dates, ISO yyyy-MM-dd. */
    val togetherSince: String? = null,
    val nextMeetDate: String? = null,
    /** Phase 10 — the other person's profile-photo version (ms), null = no photo. */
    val otherUserAvatarVersion: Long? = null,
)

data class SendMessageBody(
    val clientMsgId: String,
    val body: String? = null,
    val attachmentId: String? = null,
    val replyToMessageId: String? = null,
    /** Phase 2A: no push notification for this message. Null/absent = normal. */
    val silent: Boolean? = null,
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
    val blurHash: String? = null,
    /** Phase 2A — voice messages: base64 amplitude bytes; absent from older servers. */
    val waveform: String? = null,
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
    val replyToMessageId: String? = null,
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
    val waveform: String? = null,
)

data class PollOptionWire(val id: String, val text: String, val voterIds: List<String> = emptyList())

data class PollWire(
    val id: String,
    val conversationId: String,
    val creatorId: String,
    val question: String,
    val multiple: Boolean = false,
    val closed: Boolean = false,
    val options: List<PollOptionWire> = emptyList(),
)

data class CreatePollBody(val question: String, val options: List<String>, val multiple: Boolean)

data class VotePollBody(val optionIds: List<String>)

data class ScheduleMessageBody(val clientMsgId: String, val body: String, val sendAt: String, val silent: Boolean)

data class ScheduledMessageWire(
    val id: String,
    val conversationId: String,
    val body: String,
    val silent: Boolean = false,
    val sendAt: String,
    val status: String = "pending",
)

data class GroupInfoWire(
    val sendMessages: String = "all",
    val addMembers: String = "all",
    val editInfo: String = "all",
    val pinMessages: String = "all",
    val slowModeSeconds: Int = 0,
    val myRole: String = "member",
    val isChannel: Boolean = false,
)

data class UpdateGroupBody(
    val sendMessages: String? = null,
    val addMembers: String? = null,
    val editInfo: String? = null,
    val pinMessages: String? = null,
    val slowModeSeconds: Int? = null,
)

data class ChangeRoleBody(val role: String)

data class TransferOwnerBody(val userId: String)

data class TranslateBody(val text: String, val target: String)

data class TranslateWire(val translatedText: String, val target: String)

data class EditVersionWire(val body: String, val replacedAt: String)

data class SeenByWire(val userId: String, val displayName: String?, val phoneE164: String, val readAt: String)

data class CreateInviteBody(val expiresInHours: Int? = null, val maxUses: Int? = null)

data class InviteWire(
    val id: String,
    val code: String,
    val expiresAt: String? = null,
    val maxUses: Int? = null,
    val uses: Int = 0,
)

data class InvitePreviewWire(
    val valid: Boolean,
    val problem: String? = null,
    val title: String? = null,
    val memberCount: Int = 0,
)

data class RegisterPushTokenBody(val token: String, val provider: String = "fcm")

data class NotificationPrivacyResponse(val mode: String)

// --- Phase 4C: privacy settings, disappearing messages, two-step verification ---

/** Visibility fields are "everybody" | "nobody". */
data class PrivacySettingsResponse(
    val lastSeen: String,
    val profilePhoto: String,
    val addMe: String,
    val calls: String,
    val readReceipts: Boolean,
)

/** Every field optional: only what is non-null is sent (Moshi omits nulls), so a PUT changes just that. */
data class UpdatePrivacyBody(
    val lastSeen: String? = null,
    val profilePhoto: String? = null,
    val addMe: String? = null,
    val calls: String? = null,
    val readReceipts: Boolean? = null,
)

data class DisappearingResponse(val seconds: Int?)
data class SetDisappearingBody(val seconds: Int)

data class TwoStepStatusResponse(val enabled: Boolean, val hint: String?)
data class SetTwoStepBody(val password: String, val hint: String? = null, val currentPassword: String? = null)
data class DisableTwoStepBody(val password: String)

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
    @POST("conversations/saved")
    suspend fun getSavedConversation(): ConversationResponse

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

    // --- Phase 2A: group governance ---
    @GET("conversations/{conversationId}/group")
    suspend fun getGroupInfo(@Path("conversationId") conversationId: String): GroupInfoWire

    @PATCH("conversations/{conversationId}/group")
    suspend fun updateGroup(@Path("conversationId") conversationId: String, @Body body: UpdateGroupBody): GroupInfoWire

    @PATCH("conversations/{conversationId}/members/{userId}/role")
    suspend fun changeRole(
        @Path("conversationId") conversationId: String,
        @Path("userId") userId: String,
        @Body body: ChangeRoleBody,
    ): retrofit2.Response<Unit>

    @GET("conversations/{conversationId}/messages/{messageId}/seen-by")
    suspend fun seenBy(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
    ): List<SeenByWire>

    @GET("conversations/{conversationId}/messages/{messageId}/edits")
    suspend fun editHistory(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
    ): List<EditVersionWire>

    @POST("translate")
    suspend fun translate(@Body body: TranslateBody): TranslateWire

    @POST("conversations/{conversationId}/transfer-owner")
    suspend fun transferOwner(
        @Path("conversationId") conversationId: String,
        @Body body: TransferOwnerBody,
    ): retrofit2.Response<Unit>

    @POST("conversations/{conversationId}/invites")
    suspend fun createInvite(@Path("conversationId") conversationId: String, @Body body: CreateInviteBody): InviteWire

    @GET("conversations/{conversationId}/invites")
    suspend fun listInvites(@Path("conversationId") conversationId: String): List<InviteWire>

    @DELETE("conversations/{conversationId}/invites/{inviteId}")
    suspend fun revokeInvite(
        @Path("conversationId") conversationId: String,
        @Path("inviteId") inviteId: String,
    ): retrofit2.Response<Unit>

    @GET("group-invites/{code}")
    suspend fun previewInvite(@Path("code") code: String): InvitePreviewWire

    @POST("group-invites/{code}/join")
    suspend fun joinByInvite(@Path("code") code: String): ConversationResponse

    // --- Phase 2A: scheduled messages ---
    @POST("conversations/{conversationId}/scheduled")
    suspend fun scheduleMessage(@Path("conversationId") conversationId: String, @Body body: ScheduleMessageBody): ScheduledMessageWire

    @GET("conversations/{conversationId}/scheduled")
    suspend fun listScheduled(@Path("conversationId") conversationId: String): List<ScheduledMessageWire>

    @DELETE("scheduled/{id}")
    suspend fun cancelScheduled(@Path("id") id: String): retrofit2.Response<Unit>

    // --- Phase 2A: polls ---
    @POST("conversations/{conversationId}/polls")
    suspend fun createPoll(@Path("conversationId") conversationId: String, @Body body: CreatePollBody): PollWire

    @GET("polls/{pollId}")
    suspend fun getPoll(@Path("pollId") pollId: String): PollWire

    @PUT("polls/{pollId}/vote")
    suspend fun votePoll(@Path("pollId") pollId: String, @Body body: VotePollBody): PollWire

    @POST("polls/{pollId}/close")
    suspend fun closePoll(@Path("pollId") pollId: String): PollWire

    @GET("link-preview")
    suspend fun linkPreview(@Query("url") url: String): LinkPreviewWire

    @GET("conversations/{conversationId}/question")
    suspend fun getQuestion(@Path("conversationId") conversationId: String): QuestionResponse

    @PUT("conversations/{conversationId}/question")
    suspend fun answerQuestion(@Path("conversationId") conversationId: String, @Body body: AnswerBody)

    @GET("conversations/{conversationId}/wishlist")
    suspend fun listWishes(@Path("conversationId") conversationId: String): List<WishWire>

    @POST("conversations/{conversationId}/wishlist")
    suspend fun createWish(@Path("conversationId") conversationId: String, @Body body: CreateWishBody): WishWire

    @PATCH("conversations/{conversationId}/wishlist/{wishId}")
    suspend fun updateWish(
        @Path("conversationId") conversationId: String,
        @Path("wishId") wishId: String,
        @Body body: UpdateWishBody,
    ): WishWire

    @DELETE("conversations/{conversationId}/wishlist/{wishId}")
    suspend fun deleteWish(@Path("conversationId") conversationId: String, @Path("wishId") wishId: String)

    @PATCH("conversations/{conversationId}/pin")
    suspend fun pinMessage(@Path("conversationId") conversationId: String, @Body body: PinBody)

    @GET("conversations/{conversationId}/mood")
    suspend fun getMoods(@Path("conversationId") conversationId: String): MoodsResponse

    @PATCH("conversations/{conversationId}/mood")
    suspend fun setMood(@Path("conversationId") conversationId: String, @Body body: UpdateMoodBody)

    @GET("conversations/{conversationId}/album")
    suspend fun listAlbum(@Path("conversationId") conversationId: String): List<AlbumItemWire>

    @POST("conversations/{conversationId}/album")
    suspend fun createAlbumItem(@Path("conversationId") conversationId: String, @Body body: CreateAlbumBody): AlbumItemWire

    @POST("conversations/{conversationId}/album/{itemId}/react")
    suspend fun reactAlbumItem(
        @Path("conversationId") conversationId: String,
        @Path("itemId") itemId: String,
        @Body body: ReactAlbumBody,
    )

    @DELETE("conversations/{conversationId}/album/{itemId}")
    suspend fun deleteAlbumItem(@Path("conversationId") conversationId: String, @Path("itemId") itemId: String)

    @GET("stories/unseen")
    suspend fun unseenStories(): List<UnseenStoriesWire>

    @GET("conversations/{conversationId}/quizzes")
    suspend fun listQuizzes(@Path("conversationId") conversationId: String): List<QuizSummaryWire>

    @POST("conversations/{conversationId}/quizzes")
    suspend fun createQuiz(@Path("conversationId") conversationId: String, @Body body: CreateQuizBody): QuizSummaryWire

    @GET("conversations/{conversationId}/quizzes/{quizId}")
    suspend fun getQuiz(@Path("conversationId") conversationId: String, @Path("quizId") quizId: String): QuizDetailWire

    @POST("conversations/{conversationId}/quizzes/{quizId}/answer")
    suspend fun answerQuiz(
        @Path("conversationId") conversationId: String,
        @Path("quizId") quizId: String,
        @Body body: AnswerQuizBody,
    ): QuizResultWire

    @DELETE("conversations/{conversationId}/quizzes/{quizId}")
    suspend fun deleteQuiz(@Path("conversationId") conversationId: String, @Path("quizId") quizId: String)

    @GET("conversations/{conversationId}/vault")
    suspend fun listVault(@Path("conversationId") conversationId: String): List<VaultItemWire>

    @POST("conversations/{conversationId}/vault")
    suspend fun createVaultItem(@Path("conversationId") conversationId: String, @Body body: CreateVaultItemBody): VaultItemWire

    @DELETE("conversations/{conversationId}/vault/{itemId}")
    suspend fun deleteVaultItem(@Path("conversationId") conversationId: String, @Path("itemId") itemId: String)

    @GET("conversations/{conversationId}/stories")
    suspend fun listStories(@Path("conversationId") conversationId: String): List<StoryWire>

    @POST("conversations/{conversationId}/stories")
    suspend fun createStory(@Path("conversationId") conversationId: String, @Body body: CreateStoryBody): StoryWire

    @POST("conversations/{conversationId}/stories/{storyId}/view")
    suspend fun viewStory(@Path("conversationId") conversationId: String, @Path("storyId") storyId: String)

    @DELETE("conversations/{conversationId}/stories/{storyId}")
    suspend fun deleteStory(@Path("conversationId") conversationId: String, @Path("storyId") storyId: String)

    @GET("conversations/{conversationId}/canvas")
    suspend fun getCanvas(@Path("conversationId") conversationId: String): CanvasWire

    @POST("conversations/{conversationId}/canvas/strokes")
    suspend fun appendCanvasStrokes(@Path("conversationId") conversationId: String, @Body body: AppendStrokesBody)

    @DELETE("conversations/{conversationId}/canvas")
    suspend fun clearCanvas(@Path("conversationId") conversationId: String)

    @GET("conversations/{conversationId}/counters")
    suspend fun listCounters(@Path("conversationId") conversationId: String): List<CounterWire>

    @POST("conversations/{conversationId}/counters")
    suspend fun createCounter(@Path("conversationId") conversationId: String, @Body body: CreateCounterBody): CounterWire

    @POST("conversations/{conversationId}/counters/{counterId}/bump")
    suspend fun bumpCounter(
        @Path("conversationId") conversationId: String,
        @Path("counterId") counterId: String,
        @Body body: BumpCounterBody,
    ): CounterWire

    @DELETE("conversations/{conversationId}/counters/{counterId}")
    suspend fun deleteCounter(@Path("conversationId") conversationId: String, @Path("counterId") counterId: String)

    @GET("conversations/{conversationId}/memories")
    suspend fun listMemories(@Path("conversationId") conversationId: String): List<MemoryWire>

    @POST("conversations/{conversationId}/memories")
    suspend fun createMemory(@Path("conversationId") conversationId: String, @Body body: CreateMemoryBody): MemoryWire

    @DELETE("conversations/{conversationId}/memories/{memoryId}")
    suspend fun deleteMemory(@Path("conversationId") conversationId: String, @Path("memoryId") memoryId: String)

    @POST("conversations/{conversationId}/affection")
    suspend fun sendAffection(@Path("conversationId") conversationId: String, @Body body: AffectionBody)

    @GET("conversations/{conversationId}/letters")
    suspend fun listLetters(@Path("conversationId") conversationId: String): List<LetterWire>

    @POST("conversations/{conversationId}/letters")
    suspend fun createLetter(@Path("conversationId") conversationId: String, @Body body: CreateLetterBody): LetterWire

    @POST("conversations/{conversationId}/letters/{letterId}/open")
    suspend fun openLetter(
        @Path("conversationId") conversationId: String,
        @Path("letterId") letterId: String,
    ): LetterWire

    @PATCH("conversations/{conversationId}/us")
    suspend fun updateUs(@Path("conversationId") conversationId: String, @Body body: UpdateUsBody)

    @POST("conversations/{conversationId}/miss-you")
    suspend fun sendMissYou(@Path("conversationId") conversationId: String)

    @POST("conversations/{conversationId}/heartbeat")
    suspend fun sendHeartbeat(@Path("conversationId") conversationId: String, @Body body: HeartbeatBody): HeartbeatResponse

    @POST("conversations/{conversationId}/screen-effect")
    suspend fun sendScreenEffect(@Path("conversationId") conversationId: String, @Body body: ScreenEffectBody)

    @PUT("users/me/e2ee-key")
    suspend fun setE2eeKey(@Body body: E2eeKeyBody)

    @GET("users/{userId}/e2ee-key")
    suspend fun getE2eeKey(@Path("userId") userId: String): E2eeKeyResponse

    @POST("conversations/{conversationId}/together")
    suspend fun sendTogether(@Path("conversationId") conversationId: String, @Body body: TogetherBody)

    @PATCH("conversations/{conversationId}/settings")
    suspend fun updateConversationSettings(@Path("conversationId") conversationId: String, @Body body: UpdateConversationSettingsBody)

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

    /** Phase 11: "I received messages up to this sequence" (same body shape as /read). */
    @POST("conversations/{conversationId}/delivered")
    suspend fun markDelivered(@Path("conversationId") conversationId: String, @Body body: MarkReadBody)

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
        @Part("waveform") waveform: RequestBody? = null,
    ): AttachmentResponse

    // --- Phase 1B: resumable chunked uploads (backend: attachments/chunked-upload.*) ---

    @POST("conversations/{conversationId}/uploads")
    suspend fun initChunkedUpload(
        @Path("conversationId") conversationId: String,
        @Body body: InitChunkedUploadBody,
    ): ChunkedUploadStatus

    @GET("conversations/{conversationId}/uploads/{uploadId}")
    suspend fun chunkedUploadStatus(
        @Path("conversationId") conversationId: String,
        @Path("uploadId") uploadId: String,
    ): ChunkedUploadStatus

    @Multipart
    @PUT("conversations/{conversationId}/uploads/{uploadId}/chunks/{index}")
    suspend fun putUploadChunk(
        @Path("conversationId") conversationId: String,
        @Path("uploadId") uploadId: String,
        @Path("index") index: Int,
        @Part chunk: MultipartBody.Part,
    ): ChunkedUploadStatus

    @Multipart
    @PUT("conversations/{conversationId}/uploads/{uploadId}/thumbnail")
    suspend fun putUploadThumbnail(
        @Path("conversationId") conversationId: String,
        @Path("uploadId") uploadId: String,
        @Part thumbnail: MultipartBody.Part,
    ): ChunkedUploadAck

    @POST("conversations/{conversationId}/uploads/{uploadId}/complete")
    suspend fun completeChunkedUpload(
        @Path("conversationId") conversationId: String,
        @Path("uploadId") uploadId: String,
    ): AttachmentResponse

    // --- Phase 6: push notifications ---

    @PUT("devices/{deviceId}/push-token")
    suspend fun registerPushToken(@Path("deviceId") deviceId: String, @Body body: RegisterPushTokenBody)

    @GET("users/me/notification-privacy")
    suspend fun getNotificationPrivacy(): NotificationPrivacyResponse

    @PATCH("users/me/notification-privacy")
    suspend fun updateNotificationPrivacy(@Body body: UpdateNotificationPrivacyBody)

    // --- Phase 4C ---

    @GET("privacy")
    suspend fun getPrivacy(): PrivacySettingsResponse

    @PUT("privacy")
    suspend fun updatePrivacy(@Body body: UpdatePrivacyBody): PrivacySettingsResponse

    @GET("conversations/{conversationId}/disappearing")
    suspend fun getDisappearing(@Path("conversationId") conversationId: String): DisappearingResponse

    @PUT("conversations/{conversationId}/disappearing")
    suspend fun setDisappearing(
        @Path("conversationId") conversationId: String,
        @Body body: SetDisappearingBody,
    ): DisappearingResponse

    @GET("two-step")
    suspend fun getTwoStep(): TwoStepStatusResponse

    @PUT("two-step")
    suspend fun setTwoStep(@Body body: SetTwoStepBody): TwoStepStatusResponse

    /** DELETE with a body (the password), which Retrofit only allows through @HTTP. */
    @HTTP(method = "DELETE", path = "two-step", hasBody = true)
    suspend fun disableTwoStep(@Body body: DisableTwoStepBody): TwoStepStatusResponse

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

data class InitChunkedUploadBody(
    val originalFilename: String,
    val mimeType: String,
    val totalSize: Long,
    val chunkSize: Int,
    val durationMs: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
)

data class ChunkedUploadStatus(
    val uploadId: String,
    val totalSize: Long,
    val chunkSize: Int,
    val totalChunks: Int,
    val received: List<Int>,
)

data class ChunkedUploadAck(val ok: Boolean)
