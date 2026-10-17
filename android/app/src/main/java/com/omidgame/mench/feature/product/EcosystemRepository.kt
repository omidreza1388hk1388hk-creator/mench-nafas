package com.omidgame.mench.feature.product

import android.content.Context
import com.omidgame.mench.core.network.*
import com.omidgame.mench.core.notifications.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 1D data access. Every call returns kotlin.Result; screens never see raw exceptions. */
@Singleton
class EcosystemRepository @Inject constructor(
    private val api: EcosystemApi,
    @ApplicationContext private val context: Context,
) {
    // safety
    suspend fun report(messageId: String?, userId: String?, category: String, details: String?) =
        runCatching { api.report(ReportBody(targetUserId = userId, messageId = messageId, category = category, details = details)) }
    suspend fun restriction() = runCatching { api.myRestriction() }
    suspend fun appeal(restrictionId: String, message: String) = runCatching { api.appeal(AppealBody(restrictionId, message)) }
    suspend fun transparency() = runCatching { api.transparency() }

    // plus / stickers / referrals
    suspend fun plus() = runCatching { api.plusStatus() }
    suspend fun restorePlus() = runCatching { api.plusRestore() }
    suspend fun redeem(code: String) = runCatching { api.plusRedeem(CodeBody(code.trim())) }
    suspend fun packs() = runCatching { api.stickerPacks() }
    suspend fun referrals() = runCatching { api.referrals() }
    suspend fun claim(code: String) = runCatching { api.claimReferral(CodeBody(code.trim())) }

    // cloud
    suspend fun saveMessage(messageId: String) = runCatching { api.saveMessage(SaveMessageBody(messageId)) }
    suspend fun saveNote(text: String) = runCatching { api.saveNote(NoteBody(text)) }
    suspend fun saved(q: String?, kind: String?, folder: String? = null) = runCatching { api.saved(q?.takeIf { it.isNotBlank() }, kind, folder) }
    suspend fun deleteSaved(id: String) = runCatching { api.deleteSaved(id) }
    suspend fun cloudUsage() = runCatching { api.cloudUsage() }
    suspend fun files(q: String?, kind: String?, sort: String, dir: String) =
        runCatching { api.files(kind, q?.takeIf { it.isNotBlank() }, sort, dir) }

    suspend fun patchSaved(id: String, folder: String?, tags: List<String>) = runCatching { api.patchSaved(id, SavedPatch(folder, tags)) }
    suspend fun savedFolders() = runCatching { api.savedFolders() }

    // device linking (QR login for Web / Desktop)
    suspend fun describeLogin(id: String, nonce: String) = runCatching { api.webLoginDescribe(id, nonce) }
    suspend fun decideLogin(id: String, nonce: String, approve: Boolean) = runCatching { api.webLoginDecision(id, WebLoginDecision(nonce, approve)) }

    // planner (+ keeps local alarms in sync with whatever the server returned)
    private fun tzMinutes() = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

    suspend fun planner(filter: String) = runCatching { api.planner(filter, tzMinutes()) }
        .onSuccess { ReminderScheduler.schedule(context, it) }

    suspend fun refreshAlarms() {
        runCatching { api.planner("upcoming", tzMinutes()) + api.planner("today", tzMinutes()) }
            .onSuccess { ReminderScheduler.schedule(context, it) }
    }

    suspend fun createPlanner(body: PlannerBody) = runCatching { api.createPlanner(body) }
        .onSuccess { ReminderScheduler.schedule(context, listOf(it)) }

    suspend fun patchPlanner(id: String, body: PlannerBody) = runCatching { api.patchPlanner(id, body) }
    suspend fun complete(id: String, done: Boolean) = runCatching { api.completePlanner(id, DoneBody(done)) }
        .onSuccess { ReminderScheduler.schedule(context, listOf(it)) }

    suspend fun deletePlanner(id: String) = runCatching { api.deletePlanner(id) }
        .onSuccess { ReminderScheduler.cancel(context, id) }
}
