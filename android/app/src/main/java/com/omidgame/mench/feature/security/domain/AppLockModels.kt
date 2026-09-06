package com.omidgame.mench.feature.security.domain

/** Selectable auto-lock delays. IMMEDIATE means "lock the instant the app leaves the foreground", matching most private-messenger defaults. */
enum class AppLockTimeoutOption(val seconds: Int) {
    IMMEDIATE(0),
    AFTER_30_SECONDS(30),
    AFTER_1_MINUTE(60),
    AFTER_5_MINUTES(300),
    AFTER_15_MINUTES(900),
    ;

    companion object {
        fun fromSeconds(seconds: Int): AppLockTimeoutOption =
            entries.firstOrNull { it.seconds == seconds } ?: AFTER_30_SECONDS
    }
}
