package com.omidgame.mench.feature.product

import android.content.Context
import com.omidgame.mench.core.network.AnnouncementWire
import com.omidgame.mench.core.network.BlockBody
import com.omidgame.mench.core.network.BlockedUserResponse
import com.omidgame.mench.core.network.DeleteAccountBody
import com.omidgame.mench.core.network.EventBody
import com.omidgame.mench.core.network.FeedbackBody
import com.omidgame.mench.core.network.InviteResponse
import com.omidgame.mench.core.network.ProductApi
import com.omidgame.mench.core.network.PublicUserResponse
import com.omidgame.mench.core.network.RemoteConfigResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper over [ProductApi]. Every call returns kotlin.Result so screens never see raw exceptions. */
@Singleton
class ProductRepository @Inject constructor(
    private val api: ProductApi,
    private val prefs: ProductPrefs,
    @ApplicationContext private val context: Context,
) {
    private val _config = MutableStateFlow(RemoteConfigResponse())
    val config: StateFlow<RemoteConfigResponse> = _config

    fun flag(key: String, default: Boolean = false): Boolean = _config.value.flags[key] ?: default

    suspend fun refreshConfig(): Result<RemoteConfigResponse> = runCatching { api.config() }.onSuccess { _config.value = it }

    /** Announcements the user has not dismissed yet. */
    fun visibleAnnouncements(): List<AnnouncementWire> =
        _config.value.announcements.filter { it.id !in prefs.dismissedAnnouncements() }

    suspend fun myInvite(): Result<InviteResponse> = runCatching { api.myInvite() }
    suspend fun resolveInvite(code: String): Result<PublicUserResponse> = runCatching { api.resolveInvite(code) }
    suspend fun byUsername(username: String): Result<PublicUserResponse> = runCatching { api.byUsername(username) }

    suspend fun blocked(): Result<List<BlockedUserResponse>> = runCatching { api.blocked() }
    suspend fun block(userId: String): Result<Unit> = runCatching { api.block(BlockBody(userId)) }
    suspend fun unblock(userId: String): Result<Unit> = runCatching { api.unblock(userId) }

    /** Downloads the JSON export into the cache `exports/` folder (already covered by FileProvider). */
    suspend fun exportToFile(): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val out = File(dir, "mench-data-export.json")
            api.exportData().use { body -> out.outputStream().use { body.byteStream().copyTo(it) } }
            out
        }
    }

    suspend fun deleteAccount(confirm: String): Result<Unit> = runCatching { api.deleteAccount(DeleteAccountBody(confirm)) }

    suspend fun sendFeedback(category: String, message: String, appVersion: String?, deviceInfo: String?, logs: String?): Result<Unit> =
        runCatching { api.feedback(FeedbackBody(category, message, appVersion, deviceInfo, logs)) }

    /** Fire-and-forget, and only if the user opted in. Sends an event NAME only. */
    suspend fun track(name: String) {
        if (!prefs.analyticsEnabled) return
        runCatching { api.event(EventBody(name)) }
    }
}
