package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import com.omidgame.mench.core.network.SyncApi
import com.omidgame.mench.core.network.SyncPutBody
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DraftSyncEntryPoint { fun syncApi(): SyncApi }

/**
 * Optional cross-device draft sync (Phase 1D.1). OFF by default: drafts are plain text on the server,
 * unlike the rest of the local-only [DraftStore]. Turn on in Settings > Sync. Debounced push, pull on chat open.
 */
object DraftSync {
    private const val PREFS = "draft_sync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = HashMap<String, Job>()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("enabled", value).apply() }

    private fun api(context: Context): SyncApi =
        EntryPointAccessors.fromApplication(context.applicationContext, DraftSyncEntryPoint::class.java).syncApi()

    /** Called from [DraftStore.set]; an empty text is pushed too so a sent draft disappears on the other device. */
    fun push(context: Context, conversationId: String, text: String) {
        if (!isEnabled(context)) return
        val app = context.applicationContext
        synchronized(pending) {
            pending[conversationId]?.cancel()
            pending[conversationId] = scope.launch {
                delay(1_500)
                runCatching { api(app).put("draft:$conversationId", SyncPutBody(text)) }
            }
        }
    }

    /** Merges drafts changed on other devices into [DraftStore]. Blank remote value = cleared/sent elsewhere. */
    suspend fun pull(context: Context) {
        if (!isEnabled(context)) return
        val app = context.applicationContext
        val since = prefs(app).getString("since", null)
        val entries = runCatching { api(app).changedSince(since) }.getOrNull() ?: return
        var newest = since
        for (e in entries) {
            if (e.key.startsWith("draft:")) {
                DraftStore.setLocalOnly(app, e.key.removePrefix("draft:"), e.value)
            }
            if (newest == null || e.updatedAt > newest) newest = e.updatedAt
        }
        if (newest != null) prefs(app).edit().putString("since", newest).apply()
    }
}
