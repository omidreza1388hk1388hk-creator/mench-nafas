package com.omidgame.mench.feature.appearance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.appearance.domain.AppearanceRepository
import com.omidgame.mench.feature.appearance.domain.AppearanceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * MainActivity's sole reason to hold a ViewModel for this: it needs a
 * [StateFlow] (for `collectAsStateWithLifecycle`), and a scope that
 * survives configuration changes, both of which a bare repository call
 * doesn't give it. [AppearanceSettingsViewModel] (the actual settings
 * screen) reads and writes through the same [AppearanceRepository]; this
 * class only ever reads, so the whole app re-themes the instant a value
 * changes on that screen, with no restart and no event bus between them.
 */
@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    appearanceRepository: AppearanceRepository,
    appearanceSync: com.omidgame.mench.feature.appearance.data.AppearanceSync,
) : ViewModel() {

    // App start: adopt appearance changed on another device (no-op unless Settings > Sync is on).
    init { viewModelScope.launch { appearanceSync.pullIfEnabled() } }


    val settings: StateFlow<AppearanceSettings> = appearanceRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())
}
