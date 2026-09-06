package com.omidgame.mench.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.auth.domain.AuthFailureReason
import com.omidgame.mench.feature.auth.domain.AuthResult
import com.omidgame.mench.feature.auth.domain.RequestOtpUseCase
import com.omidgame.mench.feature.auth.domain.VerifyOtpUseCase
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
                    _uiState.value = AuthUiState.Error(messageFor(result.reason))
                }
            }
        }
    }

    fun onSubmitCode(code: String, deviceName: String) {
        val current = _uiState.value
        if (current !is AuthUiState.OtpEntry) return

        _uiState.value = current.copy(isVerifying = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = verifyOtpUseCase(current.challenge, code, deviceName)) {
                is AuthResult.Success -> _uiState.value = AuthUiState.SignedIn
                is AuthResult.Failure -> {
                    _uiState.value = current.copy(
                        isVerifying = false,
                        errorMessage = messageFor(result.reason),
                    )
                }
            }
        }
    }

    fun onResendRequested() {
        val current = _uiState.value
        if (current !is AuthUiState.OtpEntry) return
        onSubmitPhone(current.challenge.phone)
    }

    private fun messageFor(reason: AuthFailureReason): String = when (reason) {
        AuthFailureReason.NETWORK_UNAVAILABLE -> "No connection. Check your internet and try again."
        AuthFailureReason.INVALID_OR_EXPIRED_CODE -> "That code is incorrect or expired."
        AuthFailureReason.RATE_LIMITED -> "Too many attempts. Please wait before trying again."
        AuthFailureReason.UNKNOWN -> "Something went wrong. Please try again."
    }
}
