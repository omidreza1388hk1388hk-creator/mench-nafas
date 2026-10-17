package com.omidgame.mench.feature.appearance.data

import com.omidgame.mench.feature.appearance.domain.AccentColor
import com.omidgame.mench.feature.appearance.domain.AppearanceRepository
import com.omidgame.mench.feature.appearance.domain.AppearanceSettings
import com.omidgame.mench.feature.appearance.domain.BubbleStyle
import com.omidgame.mench.feature.appearance.domain.CalendarStyle
import com.omidgame.mench.feature.appearance.domain.DigitStyle
import com.omidgame.mench.feature.appearance.domain.FontScale
import com.omidgame.mench.feature.appearance.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AppearanceRepositoryImpl @Inject constructor(
    private val store: AppearanceStore,
    private val sync: AppearanceSync,
) : AppearanceRepository {

    override fun observeSettings(): Flow<AppearanceSettings> = store.settings

    // Every change is saved locally first, then pushed to the account when "sync appearance" is on.
    override suspend fun setThemeMode(mode: ThemeMode) { store.setThemeMode(mode); sync.pushIfEnabled() }

    override suspend fun setAccentColor(color: AccentColor) { store.setAccentColor(color); sync.pushIfEnabled() }

    override suspend fun setFontScale(scale: FontScale) { store.setFontScale(scale); sync.pushIfEnabled() }

    override suspend fun setBubbleStyle(style: BubbleStyle) { store.setBubbleStyle(style); sync.pushIfEnabled() }

    override suspend fun setUseDynamicColor(enabled: Boolean) = store.setUseDynamicColor(enabled)

    override suspend fun setUseAmoled(enabled: Boolean) = store.setUseAmoled(enabled)

    override suspend fun setDigitStyle(style: DigitStyle) = store.setDigitStyle(style)

    override suspend fun setCalendarStyle(style: CalendarStyle) = store.setCalendarStyle(style)

    override suspend fun setScreenSecurity(enabled: Boolean) = store.setScreenSecurity(enabled)
}
