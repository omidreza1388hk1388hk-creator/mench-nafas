package com.omidgame.mench.feature.chat.presentation

import android.content.Context

/**
 * Phase 2A — small on-device chat preferences (same plain-SharedPreferences style as [DraftStore]):
 * the emoji sent by double-tapping a message, the default skin tone of the emoji picker and the
 * recently used emoji. Local to this phone; never synced.
 */
object ChatPrefs {
    private const val PREFS = "chat_prefs"
    private const val KEY_DOUBLE_TAP = "double_tap_reaction"
    private const val KEY_TONE = "emoji_skin_tone"
    private const val KEY_RECENT = "emoji_recent"
    private const val MAX_RECENT = 24
    private const val KEY_RECENT_STICKERS = "sticker_recent"
    private const val MAX_RECENT_STICKERS = 12

    /** Red heart — this is a couple's app. */
    const val DEFAULT_DOUBLE_TAP_REACTION = "\u2764\uFE0F"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun doubleTapReaction(context: Context): String =
        prefs(context).getString(KEY_DOUBLE_TAP, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_DOUBLE_TAP_REACTION

    fun setDoubleTapReaction(context: Context, emoji: String) {
        prefs(context).edit().putString(KEY_DOUBLE_TAP, emoji).apply()
    }

    /** 0 = default yellow, 1..5 = light .. dark. */
    fun skinTone(context: Context): Int = prefs(context).getInt(KEY_TONE, 0).coerceIn(0, 5)

    fun setSkinTone(context: Context, tone: Int) {
        prefs(context).edit().putInt(KEY_TONE, tone.coerceIn(0, 5)).apply()
    }

    fun recentEmoji(context: Context): List<String> =
        prefs(context).getString(KEY_RECENT, "").orEmpty().split('|').filter { it.isNotEmpty() }

    fun pushRecentEmoji(context: Context, emoji: String) {
        val updated = pushRecent(recentEmoji(context), emoji)
        prefs(context).edit().putString(KEY_RECENT, updated.joinToString("|")).apply()
    }

    /** Recently sent sticker message bodies (see RichBody), newest first. Newline-separated: bodies never contain one. */
    fun recentStickers(context: Context): List<String> =
        prefs(context).getString(KEY_RECENT_STICKERS, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    fun pushRecentSticker(context: Context, body: String) {
        val updated = pushRecent(recentStickers(context), body, MAX_RECENT_STICKERS)
        prefs(context).edit().putString(KEY_RECENT_STICKERS, updated.joinToString("\n")).apply()
    }

    /** Most-recent-first, de-duplicated, capped. Pure, so it is unit-tested without Android. */
    internal fun pushRecent(current: List<String>, emoji: String, max: Int = MAX_RECENT): List<String> =
        (listOf(emoji) + current.filter { it != emoji }).take(max)
}
