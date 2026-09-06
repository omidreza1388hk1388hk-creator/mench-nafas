package com.omidgame.mench.feature.security.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.security.AppLockCoordinator
import com.omidgame.mench.feature.security.domain.AppLockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lives at the MainActivity/root-composable level, not on any one nav
 * route — [isLocked] must keep working no matter which screen the app was
 * showing when it got backgrounded (see AppLockCoordinator).
 */
@HiltViewModel
class AppLockGateViewModel @Inject constructor(
    private val coordinator: AppLockCoordinator,
    private val appLockRepository: AppLockRepository,
) : ViewModel() {

    val isLocked: StateFlow<Boolean> = coordinator.isLocked

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    init {
        // Belt-and-suspenders alongside AppLockCoordinator's own
        // ProcessLifecycleOwner.onStart hook — see evaluateNow()'s doc.
        viewModelScope.launch { coordinator.evaluateNow() }
    }

    fun onBiometricAvailabilityChecked(deviceSupportsIt: Boolean) {
        viewModelScope.launch {
            val enabledByUser = appLockRepository.isBiometricEnabled()
            _uiState.update { it.copy(biometricAvailable = deviceSupportsIt && enabledByUser) }
        }
    }

    fun onSubmitPin(pin: String) {
        _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
        viewModelScope.launch {
            if (appLockRepository.verifyPin(pin)) {
                _uiState.value = LockUiState(biometricAvailable = _uiState.value.biometricAvailable)
                coordinator.markUnlocked()
            } else {
                _uiState.update {
                    it.copy(isVerifying = false, errorMessage = "Incorrect PIN. Try again.")
                }
            }
        }
    }

    fun onBiometricSucceeded() {
        coordinator.markUnlocked()
    }

    fun onBiometricError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
}
