package com.omidgame.mench.feature.appearance.data

import android.content.Context
import com.omidgame.mench.core.network.SyncApi
import com.omidgame.mench.core.network.SyncPutBody
import com.omidgame.mench.feature.appearance.domain.AccentColor
import com.omidgame.mench.feature.appearance.domain.BubbleStyle
import com.omidgame.mench.feature.appearance.domain.FontScale
import com.omidgame.mench.feature.appearance.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1D.1: optional sync of appearance (theme, accent, font scale, bubble style) across the user's
 * devices through the generic sync_state store (key `setting:appearance`). OFF by default; last write wins.
 * Depends on [AppearanceStore] (not the repository) so the repository can call [pushIfEnabled] without a cycle.
 */
@Singleton
class AppearanceSync @Inject constructor(
    @ApplicationContext context: Context,
    private val store: AppearanceStore,
    private val api: SyncApi,
) {
    private val prefs = context.getSharedPreferences("appearance_sync", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) { prefs.edit().putBoolean("enabled", v).apply() }

    /** Uploads the current local appearance. Failures are ignored; the next change retries. */
    suspend fun pushIfEnabled() {
        if (!enabled) return
        val s = store.settings.first()
        val json = JSONObject()
            .put("theme", s.themeMode.wireValue)
            .put("accent", s.accentColor.wireValue)
            .put("font", s.fontScale.wireValue)
            .put("bubble", s.bubbleStyle.wireValue)
        runCatching { api.put(KEY, SyncPutBody(json.toString())) }
    }

    /** Applies the newest appearance from another device. Returns true if something was found and applied. */
    suspend fun pullIfEnabled(): Boolean {
        if (!enabled) return false
        val entry = runCatching { api.changedSince(null) }.getOrNull()?.firstOrNull { it.key == KEY } ?: return false
        val last = prefs.getString("applied_at", null)
        if (last != null && entry.updatedAt <= last) return false
        val json = runCatching { JSONObject(entry.value) }.getOrNull() ?: return false
        store.setThemeMode(ThemeMode.fromWireValue(json.optString("theme")))
        store.setAccentColor(AccentColor.fromWireValue(json.optString("accent")))
        store.setFontScale(FontScale.fromWireValue(json.optString("font")))
        store.setBubbleStyle(BubbleStyle.fromWireValue(json.optString("bubble")))
        prefs.edit().putString("applied_at", entry.updatedAt).apply()
        return true
    }

    private companion object { const val KEY = "setting:appearance" }
}
