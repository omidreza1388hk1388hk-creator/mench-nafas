package com.omidgame.mench.core.contacts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device switch for contact sync (Phase 1C leftover). When off, the address book is never read
 * and no phone number leaves the device. Default is ON so existing behaviour is unchanged.
 */
@Singleton
class ContactSyncPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("mench_contact_sync", Context.MODE_PRIVATE)

    var syncEnabled: Boolean
        get() = prefs.getBoolean("sync_enabled", true)
        set(v) { prefs.edit().putBoolean("sync_enabled", v).apply() }

    var lastSyncAtEpochMillis: Long
        get() = prefs.getLong("last_sync_at", 0L)
        set(v) { prefs.edit().putLong("last_sync_at", v).apply() }

    var lastMatchCount: Int
        get() = prefs.getInt("last_match_count", 0)
        set(v) { prefs.edit().putInt("last_match_count", v).apply() }

    /** Forget the on-device sync bookkeeping (the address book itself is never copied or cached). */
    fun clearBookkeeping() {
        prefs.edit().remove("last_sync_at").remove("last_match_count").apply()
    }
}
