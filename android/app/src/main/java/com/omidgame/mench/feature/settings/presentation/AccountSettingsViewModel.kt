package com.omidgame.mench.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.settings.domain.ProfileRepository
import com.omidgame.mench.feature.settings.domain.ProfileUpdateFailureReason
import com.omidgame.mench.feature.settings.domain.ProfileUpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    phoneE164 = profile?.phoneE164.orEmpty(),
                    displayName = profile?.displayName.orEmpty(),
                    username = profile?.username.orEmpty(),
                    bio = profile?.bio.orEmpty(),
                )
            }
        }
    }

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value, errorMessage = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun onBioChange(value: String) = _uiState.update { it.copy(bio = value, errorMessage = null) }

    fun onSave() {
        val current = _uiState.value
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = profileRepository.updateProfile(
                    displayName = current.displayName.trim().ifEmpty { null },
                    username = current.username.trim().ifEmpty { null },
                    bio = current.bio.trim(),
                )
            ) {
                is ProfileUpdateResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, saved = true) }
                }
                is ProfileUpdateResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false, errorMessage = messageFor(result.reason)) }
                }
            }
        }
    }

    private fun messageFor(reason: ProfileUpdateFailureReason): String = when (reason) {
        ProfileUpdateFailureReason.NETWORK_UNAVAILABLE -> "No connection. Check your internet and try again."
        ProfileUpdateFailureReason.USERNAME_TAKEN -> "That username is already taken."
        ProfileUpdateFailureReason.UNKNOWN -> "Something went wrong. Please try again."
    }
}
