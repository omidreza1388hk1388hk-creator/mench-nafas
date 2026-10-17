package com.omidgame.mench.core.backup

import android.content.Context
import com.omidgame.mench.feature.settings.domain.BackupReminder

/** Last successful backup time and the reminder choice. Plain prefs: a timestamp and a word, no secrets. */
object BackupStatusStore {
    private const val PREFS = "mench_backup_status"
    private const val LAST = "last_backup_at"
    private const val REMINDER = "reminder"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastBackupAt(c: Context): Long? = p(c).getLong(LAST, -1L).takeIf { it > 0 }
    fun recordSuccess(c: Context, now: Long = System.currentTimeMillis()) { p(c).edit().putLong(LAST, now).apply() }
    fun reminder(c: Context): BackupReminder = BackupReminder.fromStorage(p(c).getString(REMINDER, null))
    fun setReminder(c: Context, r: BackupReminder) { p(c).edit().putString(REMINDER, r.storageValue).apply() }
}
