package com.omidgame.mench.feature.appearance.domain

import kotlinx.coroutines.flow.Flow

/**
 * Local-only preferences — there is no server concept of "appearance",
 * so unlike [com.omidgame.mench.feature.settings.domain.ProfileRepository]
 * this never touches the network and has no cache-vs-fresh split.
 *
 * [observeSettings] backs both the settings screen (so it reflects a
 * change instantly) and [com.omidgame.mench.feature.appearance.presentation.ThemeSettingsViewModel]
 * (so the whole app re-themes live, with no restart) from the same
 * single source of truth.
 */
interface AppearanceRepository {
    fun observeSettings(): Flow<AppearanceSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAccentColor(color: AccentColor)
    suspend fun setFontScale(scale: FontScale)
    suspend fun setBubbleStyle(style: BubbleStyle)
    suspend fun setUseDynamicColor(enabled: Boolean)
    suspend fun setUseAmoled(enabled: Boolean)
    suspend fun setDigitStyle(style: DigitStyle)
    suspend fun setCalendarStyle(style: CalendarStyle)
    suspend fun setScreenSecurity(enabled: Boolean)
}
