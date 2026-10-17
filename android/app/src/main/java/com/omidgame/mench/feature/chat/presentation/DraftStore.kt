package com.omidgame.mench.feature.chat.presentation

import android.content.Context

/**
 * Unsent composer text per conversation, kept in plain SharedPreferences so
 * it survives leaving the chat and killing the app. Cleared when the text
 * is sent (the composer then writes an empty string, which removes the key).
 * Local to this phone unless the user turns on Settings > Sync ([DraftSync], off by default).
 */
object DraftStore {
    private const val PREFS = "drafts"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, conversationId: String): String =
        if (conversationId.isEmpty()) "" else prefs(context).getString(conversationId, "").orEmpty()

    fun set(context: Context, conversationId: String, text: String) {
        if (conversationId.isEmpty()) return
        // Only push when the text really changed, so merely opening a chat does not spam the server.
        if (get(context, conversationId) != text.takeUnless { it.isBlank() }.orEmpty()) DraftSync.push(context, conversationId, text)
        setLocalOnly(context, conversationId, text)
    }

    /** How many chats currently hold unsent text on this phone. */
    fun count(context: Context): Int = prefs(context).all.count { (_, v) -> (v as? String).orEmpty().isNotBlank() }

    /** Settings Phase 2 "Clear drafts": removes every unsent draft on THIS phone (nothing is pushed to the server). */
    fun clearAll(context: Context) { prefs(context).edit().clear().apply() }

    /** Writes without syncing; used when applying a draft that came from another device. */
    fun setLocalOnly(context: Context, conversationId: String, text: String) {
        if (conversationId.isEmpty()) return
        prefs(context).edit().apply {
            if (text.isBlank()) remove(conversationId) else putString(conversationId, text)
        }.apply()
    }
}
