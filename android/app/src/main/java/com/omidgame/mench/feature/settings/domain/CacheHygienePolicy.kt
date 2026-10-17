package com.omidgame.mench.feature.settings.domain

/** Rules for removing leftover temporary files. Pure, JVM-testable. */
object CacheHygienePolicy {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Allowed "keep for" choices in days; the first is the default. Temp edits are never kept forever. */
    val KEEP_DAYS_CHOICES = listOf(1, 7, 30)
    const val DEFAULT_KEEP_DAYS = 1

    fun normalizeKeepDays(days: Int): Int = if (days in KEEP_DAYS_CHOICES) days else DEFAULT_KEEP_DAYS

    /** A file is expired when it has not been modified for at least [keepDays] days. Unknown (0) timestamps are kept. */
    fun isExpired(lastModifiedMs: Long, nowMs: Long, keepDays: Int): Boolean =
        lastModifiedMs > 0 && nowMs - lastModifiedMs >= normalizeKeepDays(keepDays) * DAY_MS
}
