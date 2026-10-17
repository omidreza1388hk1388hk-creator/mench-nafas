package com.omidgame.mench.feature.privacy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.PrivacySettingsResponse
import com.omidgame.mench.core.network.UpdatePrivacyBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PrivacyUiState {
    data object Loading : PrivacyUiState
    data class Content(val settings: PrivacySettingsResponse, val saveFailed: Boolean = false) : PrivacyUiState
    data object Error : PrivacyUiState
}

/** Talks to ChatApi directly, like NotificationPrivacyViewModel: this is server-side state with no offline story. */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val chatApi: ChatApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrivacyUiState>(PrivacyUiState.Loading)
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = PrivacyUiState.Loading
        viewModelScope.launch {
            _uiState.value = runCatching { chatApi.getPrivacy() }
                .fold(onSuccess = { PrivacyUiState.Content(it) }, onFailure = { PrivacyUiState.Error })
        }
    }

    /** Optimistic: the switch moves immediately; if the server refuses, it moves back and a hint shows. */
    fun update(patch: UpdatePrivacyBody) {
        val before = (_uiState.value as? PrivacyUiState.Content)?.settings ?: return
        val optimistic = before.copy(
            lastSeen = patch.lastSeen ?: before.lastSeen,
            profilePhoto = patch.profilePhoto ?: before.profilePhoto,
            addMe = patch.addMe ?: before.addMe,
            calls = patch.calls ?: before.calls,
            readReceipts = patch.readReceipts ?: before.readReceipts,
        )
        _uiState.value = PrivacyUiState.Content(optimistic)
        viewModelScope.launch {
            runCatching { chatApi.updatePrivacy(patch) }
                .onSuccess { _uiState.value = PrivacyUiState.Content(it) }
                .onFailure { _uiState.value = PrivacyUiState.Content(before, saveFailed = true) }
        }
    }
}
