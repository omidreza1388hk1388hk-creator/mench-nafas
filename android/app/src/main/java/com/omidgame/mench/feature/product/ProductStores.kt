package com.omidgame.mench.feature.product

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small on-device flags for Phase 1C surfaces: onboarding completion, dismissed announcements,
 * analytics consent and rating-prompt bookkeeping. Plain SharedPreferences (no secrets).
 */
@Singleton
class ProductPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("mench_product", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(v) { prefs.edit().putBoolean("onboarding_done", v).apply() }

    /** Analytics is OFF until the user opts in (privacy-respecting default). Counters only, never content. */
    var analyticsEnabled: Boolean
        get() = prefs.getBoolean("analytics_enabled", false)
        set(v) { prefs.edit().putBoolean("analytics_enabled", v).apply() }

    fun dismissedAnnouncements(): Set<String> = prefs.getStringSet("dismissed_announcements", emptySet()) ?: emptySet()
    fun dismissAnnouncement(id: String) {
        prefs.edit().putStringSet("dismissed_announcements", dismissedAnnouncements() + id).apply()
    }

    // ---- rating prompt: ask once after real use, then rarely ----
    fun recordAppOpen() {
        if (prefs.getLong("first_open_at", 0L) == 0L) prefs.edit().putLong("first_open_at", System.currentTimeMillis()).apply()
        prefs.edit().putInt("open_count", prefs.getInt("open_count", 0) + 1).apply()
    }

    /** >= 12 opens, >= 7 days since first open, never answered "no thanks", not asked within 60 days. */
    fun shouldAskForRating(now: Long = System.currentTimeMillis()): Boolean {
        if (prefs.getBoolean("rating_never", false)) return false
        val first = prefs.getLong("first_open_at", now)
        val lastAsked = prefs.getLong("rating_last_asked", 0L)
        return prefs.getInt("open_count", 0) >= 12 &&
            now - first >= 7L * 24 * 3600_000 &&
            (lastAsked == 0L || now - lastAsked >= 60L * 24 * 3600_000)
    }

    fun markRatingAsked() { prefs.edit().putLong("rating_last_asked", System.currentTimeMillis()).apply() }
    fun neverAskRating() { prefs.edit().putBoolean("rating_never", true).apply() }
}
