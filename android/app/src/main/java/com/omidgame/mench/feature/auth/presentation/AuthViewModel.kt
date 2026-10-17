package com.omidgame.mench.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.auth.domain.AuthFailureReason
import com.omidgame.mench.feature.auth.domain.AuthResult
import com.omidgame.mench.feature.auth.domain.RequestOtpUseCase
import com.omidgame.mench.feature.auth.domain.VerifyOtpUseCase
import com.omidgame.mench.feature.auth.domain.VerifyTwoStepUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val requestOtpUseCase: RequestOtpUseCase,
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val verifyTwoStepUseCase: VerifyTwoStepUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.PhoneEntry)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onSubmitPhone(phoneE164: String) {
        _uiState.value = AuthUiState.RequestingOtp
        viewModelScope.launch {
            when (val result = requestOtpUseCase(phoneE164)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState.OtpEntry(challenge = result.value)
                }
                is AuthResult.Failure -> {
                    _uiState.value = AuthUiState.Error(result.reason)
                }
                is AuthResult.TwoStepRequired -> Unit // never returned by requestOtp
            }
        }
    }

    fun onSubmitCode(code: String, deviceName: String) {
        val current = _uiState.value
        if (current !is AuthUiState.OtpEntry) return

        _uiState.value = current.copy(isVerifying = true, errorReason = null)
        viewModelScope.launch {
            when (val result = verifyOtpUseCase(current.challenge, code, deviceName)) {
                is AuthResult.Success -> _uiState.value = AuthUiState.SignedIn
                is AuthResult.TwoStepRequired -> _uiState.value = AuthUiState.TwoStepEntry(result.challenge)
                is AuthResult.Failure -> {
                    _uiState.value = current.copy(
                        isVerifying = false,
                        errorReason = result.reason,
                    )
                }
            }
        }
    }

    fun onSubmitTwoStepPassword(password: String) {
        val current = _uiState.value
        if (current !is AuthUiState.TwoStepEntry || current.isVerifying) return

        _uiState.value = current.copy(isVerifying = true, errorReason = null)
        viewModelScope.launch {
            when (val result = verifyTwoStepUseCase(current.challenge, password)) {
                is AuthResult.Success -> _uiState.value = AuthUiState.SignedIn
                is AuthResult.Failure -> _uiState.value = current.copy(
                    isVerifying = false,
                    errorReason = result.reason,
                )
                is AuthResult.TwoStepRequired -> Unit // not returned by this call
            }
        }
    }

    fun onResendRequested() {
        val current = _uiState.value
        if (current !is AuthUiState.OtpEntry || current.isVerifying) return

        // Stay on the code screen while resending (and on failure), instead of bouncing to the phone screen.
        _uiState.value = current.copy(isVerifying = true, errorReason = null)
        viewModelScope.launch {
            when (val result = requestOtpUseCase(current.challenge.phone)) {
                is AuthResult.Success -> _uiState.value = AuthUiState.OtpEntry(challenge = result.value)
                is AuthResult.Failure -> _uiState.value = current.copy(
                    isVerifying = false,
                    errorReason = result.reason,
                )
                is AuthResult.TwoStepRequired -> Unit // never returned by requestOtp
            }
        }
    }
}
