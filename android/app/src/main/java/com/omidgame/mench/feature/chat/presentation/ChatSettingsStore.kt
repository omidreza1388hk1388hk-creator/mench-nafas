package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.omidgame.mench.feature.settings.domain.ChatSettingsRules
import com.omidgame.mench.feature.settings.domain.ChatSettingsSnapshot
import com.omidgame.mench.feature.settings.domain.PhotoQuality
import com.omidgame.mench.feature.settings.domain.SwipeReplyDirection

/**
 * Storage for the "Chats" settings (plain prefs, none of it secret). [current] is an in-memory copy so code
 * without a Context (the repository) can read it; it is loaded in Application.onCreate and refreshed on every write.
 * Until loaded it holds the defaults, which equal the app's behaviour before these settings existed.
 */
object ChatSettingsStore {
    private const val PREFS = "mench_chat_settings"

    @Volatile var current: ChatSettingsSnapshot = ChatSettingsSnapshot()
        private set

    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): ChatSettingsSnapshot {
        val p = prefs(context)
        val snap = ChatSettingsSnapshot(
            messageTextScale = ChatSettingsRules.clampTextScale(p.getFloat("text_scale", 1f)),
            lineSpacing = ChatSettingsRules.clampLineSpacing(p.getFloat("line_spacing", 1f)),
            enterSends = p.getBoolean("enter_sends", false),
            autoCapitalize = p.getBoolean("auto_capitalize", true),
            spellCheck = p.getBoolean("spell_check", true),
            linkPreviews = p.getBoolean("link_previews", true),
            quickReactions = ChatSettingsRules.decodeReactions(p.getString("quick_reactions", null)),
            swipeReply = SwipeReplyDirection.fromStorage(p.getString("swipe_reply", null)),
            keepArchived = p.getBoolean("keep_archived", false),
            photoQuality = PhotoQuality.fromStorage(p.getString("photo_quality", null)),
            stripLocation = p.getBoolean("strip_location", true),
            viewOnceDefault = p.getBoolean("view_once_default", false),
        )
        current = snap
        return snap
    }

    fun update(context: Context, block: SharedPreferences.Editor.() -> Unit) {
        prefs(context).edit().apply(block).apply()
        load(context)
    }

    fun setTextScale(c: Context, v: Float) = update(c) { putFloat("text_scale", ChatSettingsRules.clampTextScale(v)) }
    fun setLineSpacing(c: Context, v: Float) = update(c) { putFloat("line_spacing", ChatSettingsRules.clampLineSpacing(v)) }
    fun setEnterSends(c: Context, v: Boolean) = update(c) { putBoolean("enter_sends", v) }
    fun setAutoCapitalize(c: Context, v: Boolean) = update(c) { putBoolean("auto_capitalize", v) }
    fun setSpellCheck(c: Context, v: Boolean) = update(c) { putBoolean("spell_check", v) }
    fun setLinkPreviews(c: Context, v: Boolean) = update(c) { putBoolean("link_previews", v) }
    fun setQuickReactions(c: Context, list: List<String>) = update(c) { putString("quick_reactions", ChatSettingsRules.encodeReactions(list)) }
    fun setSwipeReply(c: Context, v: SwipeReplyDirection) = update(c) { putString("swipe_reply", v.storageValue) }
    fun setKeepArchived(c: Context, v: Boolean) = update(c) { putBoolean("keep_archived", v) }
    fun setPhotoQuality(c: Context, v: PhotoQuality) = update(c) { putString("photo_quality", v.storageValue) }
    fun setStripLocation(c: Context, v: Boolean) = update(c) { putBoolean("strip_location", v) }
    fun setViewOnceDefault(c: Context, v: Boolean) = update(c) { putBoolean("view_once_default", v) }
}

/** Live snapshot for composables: recomposes when any chat setting changes (e.g. after returning from settings). */
@Composable
fun rememberChatSettings(): ChatSettingsSnapshot {
    val context = LocalContext.current
    var snap by remember { mutableStateOf(ChatSettingsStore.load(context)) }
    DisposableEffect(context) {
        val p = context.applicationContext.getSharedPreferences("mench_chat_settings", Context.MODE_PRIVATE)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> snap = ChatSettingsStore.load(context) }
        p.registerOnSharedPreferenceChangeListener(l)
        onDispose { p.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return snap
}
