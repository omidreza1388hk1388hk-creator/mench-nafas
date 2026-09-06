package com.omidgame.mench.feature.settings.presentation

data class AccountSettingsUiState(
    val isLoading: Boolean = true,
    val phoneE164: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)
