package com.omidgame.mench.feature.chat.presentation

import android.content.Context

/**
 * Phase 2A — on-device chat-list state (same plain-SharedPreferences style as [DraftStore]):
 * the user's folders and the chats they manually marked "unread". Never synced to the server.
 */
object ChatListPrefs {
    private const val PREFS = "chat_list_prefs"
    private const val KEY_FOLDERS = "folders"
    private const val KEY_MANUAL_UNREAD = "manual_unread"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folders(context: Context): List<ChatFolder> =
        ChatFolders.decode(prefs(context).getString(KEY_FOLDERS, null))

    fun saveFolders(context: Context, folders: List<ChatFolder>) {
        prefs(context).edit().putString(KEY_FOLDERS, ChatFolders.encode(folders)).apply()
    }

    fun manualUnread(context: Context): Set<String> =
        // getStringSet's result must not be modified, so always hand out a copy.
        prefs(context).getStringSet(KEY_MANUAL_UNREAD, emptySet()).orEmpty().toSet()

    fun saveManualUnread(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_MANUAL_UNREAD, HashSet(ids)).apply()
    }
}
