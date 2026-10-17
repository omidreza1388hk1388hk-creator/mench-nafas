package com.omidgame.mench.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ---- safety ----
data class ReportBody(val targetUserId: String? = null, val conversationId: String? = null, val messageId: String? = null, val category: String, val details: String? = null)
data class ReportResponse(val id: String)
data class RestrictionResponse(
    val restricted: Boolean = false, val id: String? = null, val kind: String? = null, val reason: String? = null,
    val expiresAt: String? = null, val automatic: Boolean = false, val appealStatus: String? = null,
)
data class AppealBody(val restrictionId: String, val message: String)
data class CountRow(val category: String? = null, val status: String? = null, val kind: String? = null, val automatic: Boolean? = null, val n: Int = 0)
data class TransparencyResponse(
    val periodDays: Int = 90,
    val reportsByCategory: List<CountRow> = emptyList(),
    val reportsByStatus: List<CountRow> = emptyList(),
    val restrictions: List<CountRow> = emptyList(),
    val appeals: List<CountRow> = emptyList(),
    val howToReport: String = "",
    val policies: String = "",
)

// ---- plus / stickers / referrals ----
data class PlusStatus(val active: Boolean = false, val expiresAt: String? = null, val source: String? = null, val graceUntil: String? = null, val cloudQuotaBytes: Long = 0)
data class CodeBody(val code: String)
data class StickerPack(val id: String, val name: String, val premium: Boolean, val locked: Boolean, val glyphs: List<String>)
data class ReferralRule(val qualifyMessages: Int = 0, val qualifyConversations: Int = 0, val rewardPlusDays: Int = 0, val maxRewards: Int = 0)
data class ReferralStats(val invited: Int = 0, val rewarded: Int = 0, val pending: Int = 0)
data class ReferralEntry(val status: String, val createdAt: String, val qualifiedAt: String? = null, val initial: String = "?")
data class ReferralsResponse(
    val code: String, val link: String, val rewardsEnabled: Boolean = false,
    val rule: ReferralRule = ReferralRule(), val stats: ReferralStats = ReferralStats(), val history: List<ReferralEntry> = emptyList(),
)

// ---- saved / files ----
data class SaveMessageBody(val messageId: String, val folder: String? = null)
data class NoteBody(val body: String, val folder: String? = null, val tags: List<String>? = null)
data class SavedSource(val conversationId: String?, val messageId: String?, val senderName: String?, val sentAt: String?)
data class SavedAttachment(val id: String, val filename: String?, val mime: String?, val sizeBytes: Long = 0)
data class SavedItem(
    val id: String, val kind: String, val text: String?, val folder: String?, val tags: List<String> = emptyList(),
    val savedAt: String, val source: SavedSource? = null, val attachment: SavedAttachment? = null,
)
data class SavedPatch(val folder: String?, val tags: List<String>)
data class FolderCount(val folder: String, val n: Int)
data class TagCount(val tag: String, val n: Int)
data class SavedFolders(val folders: List<FolderCount> = emptyList(), val tags: List<TagCount> = emptyList())
data class FileEntry(
    val id: String, val kind: String, val filename: String, val mime: String, val sizeBytes: Long, val createdAt: String,
    val conversationId: String? = null, val messageId: String? = null, val senderId: String? = null, val senderName: String? = null,
)
data class KindUsage(val kind: String, val count: Int, val bytes: Long)
data class SavedUsage(val count: Int = 0, val bytes: Long = 0)
data class CloudUsageResponse(val byKind: List<KindUsage> = emptyList(), val saved: SavedUsage = SavedUsage())

// ---- planner ----
data class ChecklistItem(val text: String, val done: Boolean)
data class PlannerItem(
    val id: String, val kind: String, val title: String, val note: String? = null, val dueAt: String? = null,
    val repeat: String = "none", val remindMinutesBefore: Int = 0, val location: String? = null,
    val checklist: List<ChecklistItem> = emptyList(), val conversationId: String? = null, val messageId: String? = null,
    val completedAt: String? = null, val participants: List<String> = emptyList(),
)
data class PlannerBody(
    val kind: String? = null, val title: String, val note: String? = null, val dueAt: String? = null,
    val repeat: String? = null, val remindMinutesBefore: Int? = null, val location: String? = null,
    val checklist: List<ChecklistItem>? = null, val conversationId: String? = null, val messageId: String? = null,
    val participants: List<String>? = null,
)
data class DoneBody(val done: Boolean)

data class WebLoginInfo(val platform: String, val deviceName: String, val requestedAt: String? = null)
data class WebLoginDecision(val nonce: String, val approve: Boolean)

/** Phase 1D endpoints (backend: modules/ecosystem). */
interface EcosystemApi {
    @POST("reports") suspend fun report(@Body body: ReportBody): ReportResponse
    @GET("safety/restriction") suspend fun myRestriction(): RestrictionResponse
    @POST("safety/appeals") suspend fun appeal(@Body body: AppealBody)
    @GET("transparency") suspend fun transparency(): TransparencyResponse

    @GET("plus/status") suspend fun plusStatus(): PlusStatus
    @POST("plus/restore") suspend fun plusRestore(): PlusStatus
    @POST("plus/redeem") suspend fun plusRedeem(@Body body: CodeBody): PlusStatus
    @GET("stickers/packs") suspend fun stickerPacks(): List<StickerPack>

    @GET("referrals/mine") suspend fun referrals(): ReferralsResponse
    @POST("referrals/claim") suspend fun claimReferral(@Body body: CodeBody)

    @POST("saved/message") suspend fun saveMessage(@Body body: SaveMessageBody): ReportResponse
    @POST("saved/note") suspend fun saveNote(@Body body: NoteBody): ReportResponse
    @GET("saved") suspend fun saved(@Query("q") q: String? = null, @Query("kind") kind: String? = null, @Query("folder") folder: String? = null): List<SavedItem>
    @DELETE("saved/{id}") suspend fun deleteSaved(@Path("id") id: String)
    @GET("saved/usage") suspend fun cloudUsage(): CloudUsageResponse
    @GET("files") suspend fun files(
        @Query("kind") kind: String? = null, @Query("q") q: String? = null,
        @Query("sort") sort: String = "date", @Query("dir") dir: String = "desc",
    ): List<FileEntry>

    @GET("web-login/{id}") suspend fun webLoginDescribe(@Path("id") id: String, @Query("nonce") nonce: String): WebLoginInfo
    @POST("web-login/{id}/decision") suspend fun webLoginDecision(@Path("id") id: String, @Body body: WebLoginDecision)
    @PATCH("saved/{id}") suspend fun patchSaved(@Path("id") id: String, @Body body: SavedPatch)
    @GET("saved/folders") suspend fun savedFolders(): SavedFolders

    @GET("planner") suspend fun planner(@Query("filter") filter: String, @Query("tzOffsetMinutes") tz: Int): List<PlannerItem>
    @POST("planner") suspend fun createPlanner(@Body body: PlannerBody): PlannerItem
    @POST("planner/{id}/complete") suspend fun completePlanner(@Path("id") id: String, @Body body: DoneBody): PlannerItem
    @PATCH("planner/{id}") suspend fun patchPlanner(@Path("id") id: String, @Body body: PlannerBody): PlannerItem
    @DELETE("planner/{id}") suspend fun deletePlanner(@Path("id") id: String)
}
