package com.omidgame.mench.feature.appearance.presentation

import com.omidgame.mench.feature.appearance.domain.AppearanceSettings

data class AppearanceSettingsUiState(
    val isLoading: Boolean = true,
    val settings: AppearanceSettings = AppearanceSettings(),
)
