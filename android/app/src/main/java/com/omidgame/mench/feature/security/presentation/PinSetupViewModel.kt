package com.omidgame.mench.feature.security.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.security.domain.AppLockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_PIN_LENGTH = 4

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mode: PinSetupMode = PinSetupMode.valueOf(checkNotNull(savedStateHandle["mode"]))

    private val _uiState = MutableStateFlow(PinSetupUiState.initial(mode))
    val uiState: StateFlow<PinSetupUiState> = _uiState.asStateFlow()

    /** Held only in memory for the duration of this flow, never persisted — only the final PinHasher output is. */
    private var newPinPendingConfirmation: String? = null

    fun onSubmit(input: String) {
        val current = _uiState.value
        if (input.length < MIN_PIN_LENGTH) {
            _uiState.update { it.copy(errorMessage = "PIN must be at least $MIN_PIN_LENGTH digits.") }
            return
        }

        when (current.step) {
            PinSetupStep.EnterCurrent -> verifyCurrent(input)
            PinSetupStep.EnterNew -> {
                newPinPendingConfirmation = input
                _uiState.update { it.copy(step = PinSetupStep.ConfirmNew, errorMessage = null) }
            }
            PinSetupStep.ConfirmNew -> confirmAndSave(input)
        }
    }

    private fun verifyCurrent(pin: String) {
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            if (!appLockRepository.verifyPin(pin)) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Incorrect current PIN.") }
                return@launch
            }

            when (mode) {
                PinSetupMode.DISABLE -> {
                    appLockRepository.disableAppLock()
                    _uiState.update { it.copy(isSaving = false, completed = true) }
                }
                PinSetupMode.CHANGE -> {
                    _uiState.update { it.copy(isSaving = false, step = PinSetupStep.EnterNew) }
                }
                PinSetupMode.CREATE -> Unit // unreachable: CREATE starts at EnterNew
            }
        }
    }

    private fun confirmAndSave(confirmation: String) {
        val newPin = newPinPendingConfirmation
        if (newPin == null || confirmation != newPin) {
            _uiState.update {
                it.copy(
                    step = PinSetupStep.EnterNew,
                    errorMessage = "PINs didn't match. Try again.",
                )
            }
            newPinPendingConfirmation = null
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            appLockRepository.setPin(newPin)
            _uiState.update { it.copy(isSaving = false, completed = true) }
        }
    }
}
