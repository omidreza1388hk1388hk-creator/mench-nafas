package com.omidgame.mench.feature.appearance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.appearance.domain.AccentColor
import com.omidgame.mench.feature.appearance.domain.AppearanceRepository
import com.omidgame.mench.feature.appearance.domain.BubbleStyle
import com.omidgame.mench.feature.appearance.domain.CalendarStyle
import com.omidgame.mench.feature.appearance.domain.DigitStyle
import com.omidgame.mench.feature.appearance.domain.FontScale
import com.omidgame.mench.feature.appearance.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val appearanceRepository: AppearanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppearanceSettingsUiState())
    val uiState: StateFlow<AppearanceSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appearanceRepository.observeSettings().collect { settings ->
                _uiState.update { it.copy(isLoading = false, settings = settings) }
            }
        }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { appearanceRepository.setThemeMode(mode) }
    }

    fun onAccentColorSelected(color: AccentColor) {
        viewModelScope.launch { appearanceRepository.setAccentColor(color) }
    }

    fun onFontScaleSelected(scale: FontScale) {
        viewModelScope.launch { appearanceRepository.setFontScale(scale) }
    }

    fun onBubbleStyleSelected(style: BubbleStyle) {
        viewModelScope.launch { appearanceRepository.setBubbleStyle(style) }
    }

    fun onAmoledSelected(enabled: Boolean) {
        viewModelScope.launch { appearanceRepository.setUseAmoled(enabled) }
    }

    fun onDynamicColorSelected(enabled: Boolean) {
        viewModelScope.launch { appearanceRepository.setUseDynamicColor(enabled) }
    }

    /** Applies a curated theme: mode, accent and bubble shape together, and turns Material You off so the preset's colours show. */
    fun onPresetSelected(preset: ThemePreset) {
        viewModelScope.launch {
            appearanceRepository.setUseDynamicColor(false)
            appearanceRepository.setUseAmoled(preset.amoled)
            appearanceRepository.setThemeMode(preset.mode)
            appearanceRepository.setAccentColor(preset.accent)
            appearanceRepository.setBubbleStyle(preset.bubbleStyle)
        }
    }

    fun onDigitStyleSelected(style: DigitStyle) {
        viewModelScope.launch { appearanceRepository.setDigitStyle(style) }
    }

    fun onCalendarStyleSelected(style: CalendarStyle) {
        viewModelScope.launch { appearanceRepository.setCalendarStyle(style) }
    }

    fun onScreenSecurityChanged(enabled: Boolean) {
        viewModelScope.launch { appearanceRepository.setScreenSecurity(enabled) }
    }
}
