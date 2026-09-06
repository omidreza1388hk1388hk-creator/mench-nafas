package com.omidgame.mench.feature.security.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.auth.domain.AuthRepository
import com.omidgame.mench.feature.security.domain.AppLockRepository
import com.omidgame.mench.feature.security.domain.AppLockTimeoutOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Enabling/disabling the PIN itself is handled by a dedicated flow
 * ([PinSetupScreen], which always requires proving the current PIN before
 * changing or removing it) — this ViewModel only toggles the settings that
 * don't need that extra proof: biometric opt-in and the auto-lock delay,
 * both of which are meaningless unless a PIN already exists.
 */
@HiltViewModel
class PrivacySecurityViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacySecurityUiState())
    val uiState: StateFlow<PrivacySecurityUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    appLockEnabled = appLockRepository.isAppLockEnabled(),
                    biometricEnabled = appLockRepository.isBiometricEnabled(),
                    autoLockTimeout = appLockRepository.autoLockTimeout(),
                )
            }
        }
    }

    fun onBiometricAvailabilityChecked(available: Boolean) {
        _uiState.update { it.copy(biometricAvailableOnDevice = available) }
    }

    fun onBiometricToggle(enabled: Boolean) {
        viewModelScope.launch {
            val applied = appLockRepository.setBiometricEnabled(enabled)
            _uiState.update {
                it.copy(
                    biometricEnabled = if (applied) enabled else it.biometricEnabled,
                    infoMessage = if (!applied) "Set a PIN first to enable biometric unlock." else null,
                )
            }
        }
    }

    fun onTimeoutSelected(option: AppLockTimeoutOption) {
        viewModelScope.launch {
            appLockRepository.setAutoLockTimeout(option)
            _uiState.update { it.copy(autoLockTimeout = option) }
        }
    }

    fun onLogoutAllDevices() {
        _uiState.update { it.copy(isLoggingOutAll = true) }
        viewModelScope.launch {
            authRepository.logoutAllDevices()
            _uiState.update { it.copy(isLoggingOutAll = false, loggedOutAll = true) }
        }
    }
}
