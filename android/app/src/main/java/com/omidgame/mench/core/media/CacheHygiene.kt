package com.omidgame.mench.core.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omidgame.mench.feature.settings.domain.CacheHygienePolicy
import java.io.File

/**
 * Cleans the edited-photo scratch folder (`cache/photo-edits`). These files are only ever copies made while editing a
 * photo for sending; the outbox keeps its own cached payloads, so nothing queued for upload lives here.
 */
object CacheHygiene {
    private const val PREFS = "mench_cache_hygiene"
    private const val KEEP_DAYS = "keep_days"

    private fun dir(context: Context) = File(context.cacheDir, "photo-edits")

    fun keepDays(context: Context): Int =
        CacheHygienePolicy.normalizeKeepDays(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEEP_DAYS, CacheHygienePolicy.DEFAULT_KEEP_DAYS))

    fun setKeepDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEEP_DAYS, CacheHygienePolicy.normalizeKeepDays(days)).apply()
    }

    fun sizeBytes(context: Context): Long = dir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Deletes expired edits; returns how many files were removed. */
    fun cleanExpired(context: Context, now: Long = System.currentTimeMillis()): Int {
        val keep = keepDays(context)
        return dir(context).listFiles().orEmpty()
            .filter { it.isFile && CacheHygienePolicy.isExpired(it.lastModified(), now, keep) }
            .count { it.delete() }
    }

    /** Settings "Clear now": removes every edited-photo scratch file. */
    fun clearAll(context: Context): Int = dir(context).listFiles().orEmpty().filter { it.isFile }.count { it.delete() }
}

class CacheHygieneWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { CacheHygiene.cleanExpired(applicationContext) }
        return Result.success()
    }
}
