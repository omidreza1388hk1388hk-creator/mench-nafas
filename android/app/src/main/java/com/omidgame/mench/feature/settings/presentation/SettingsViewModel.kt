package com.omidgame.mench.feature.settings.presentation

import android.app.KeyguardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.auth.domain.AuthRepository
import com.omidgame.mench.feature.security.domain.AppLockRepository
import com.omidgame.mench.feature.settings.data.SettingsResetter
import com.omidgame.mench.feature.settings.domain.ProfileRepository
import com.omidgame.mench.feature.settings.domain.SettingsCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val chatRepository: com.omidgame.mench.feature.chat.domain.ChatRepository,
    private val realtimeServiceLauncher: com.omidgame.mench.core.notifications.RealtimeServiceLauncher,
    private val appLockRepository: AppLockRepository,
    @ApplicationContext private val appContext: Context,
    private val settingsResetter: SettingsResetter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            _uiState.update {
                it.copy(
                    displayName = profile?.displayName,
                    phoneE164 = profile?.phoneE164,
                    userId = profile?.id,
                    avatarVersion = profile?.avatarVersion,
                )
            }
            refreshSecurityFacts()
        }
    }

    /** Re-reads the facts behind the category summaries; called on start and whenever the screen resumes. */
    fun refreshSecurityFacts() {
        viewModelScope.launch {
            val lockOn = appLockRepository.isAppLockEnabled()
            val bio = lockOn && appLockRepository.isBiometricEnabled()
            val secure = appContext.getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: true
            _uiState.update { it.copy(appLockEnabled = lockOn, biometricEnabled = bio, deviceScreenLockSet = secure) }
        }
    }

    /** Resets one category (null = all). Never touches messages, accounts or keys; see SettingsResetPlan. */
    fun resetSettings(category: SettingsCategory?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            if (category == null) settingsResetter.resetAll() else settingsResetter.resetCategory(category)
            refreshSecurityFacts()
            onDone()
        }
    }

    fun onLogout() {
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            realtimeServiceLauncher.stop()
            chatRepository.disconnectRealtime()
            authRepository.logout()
            _uiState.update { it.copy(isLoggingOut = false, loggedOut = true) }
        }
    }
}
