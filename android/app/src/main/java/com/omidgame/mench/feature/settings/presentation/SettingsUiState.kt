package com.omidgame.mench.feature.settings.presentation

data class SettingsUiState(
    val displayName: String? = null,
    val phoneE164: String? = null,
    val isLoggingOut: Boolean = false,
    val loggedOut: Boolean = false,
)
