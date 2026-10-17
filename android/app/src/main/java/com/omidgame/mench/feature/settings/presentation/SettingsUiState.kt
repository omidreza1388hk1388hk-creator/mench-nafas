package com.omidgame.mench.feature.settings.presentation

data class SettingsUiState(
    val displayName: String? = null,
    val phoneE164: String? = null,
    val userId: String? = null,
    val avatarVersion: Long? = null,
    val isLoggingOut: Boolean = false,
    val loggedOut: Boolean = false,
    /** Live facts for the home-screen category summaries (Phase 1A). Defaults keep old call sites valid. */
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val deviceScreenLockSet: Boolean = true,
)
