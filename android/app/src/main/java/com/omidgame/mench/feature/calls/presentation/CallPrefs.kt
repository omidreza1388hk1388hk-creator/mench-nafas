package com.omidgame.mench.feature.calls.presentation

import android.content.Context

/** Phase 2B — remembers when the call log was last opened, so the missed-call badge counts only what is new. */
object CallPrefs {
    private const val PREFS = "call_prefs"
    private const val KEY_LAST_SEEN = "history_last_seen"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastSeenMillis(context: Context): Long = prefs(context).getLong(KEY_LAST_SEEN, 0L)

    fun markSeenNow(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_SEEN, System.currentTimeMillis()).apply()
    }
}
