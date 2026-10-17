package com.omidgame.mench.feature.chat.presentation

/**
 * Phase 3A — "sync hearts": when you and your partner hold the heart at (about) the same time, both phones celebrate.
 * Heartbeats travel on release, so "at the same time" means: a partner heartbeat arrives while you are holding, or within
 * [WINDOW_MS] after you let go.
 */
object HeartbeatSync {
    const val WINDOW_MS = 6_000L

    @Volatile
    var holding: Boolean = false
        private set

    @Volatile
    private var lastReleaseAt: Long = Long.MIN_VALUE / 2

    fun onPress() {
        holding = true
    }

    fun onRelease(nowMillis: Long) {
        holding = false
        lastReleaseAt = nowMillis
    }

    /** True if a heartbeat arriving at [nowMillis] counts as simultaneous with your own. */
    fun isSynced(nowMillis: Long): Boolean = isSyncedAt(holding, lastReleaseAt, nowMillis)
}

/** Pure form of the rule, for tests. */
internal fun isSyncedAt(holding: Boolean, lastReleaseAt: Long, nowMillis: Long, windowMs: Long = HeartbeatSync.WINDOW_MS): Boolean =
    holding || (nowMillis - lastReleaseAt) in 0..windowMs
