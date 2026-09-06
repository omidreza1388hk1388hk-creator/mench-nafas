package com.omidgame.mench.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.UpdateNotificationPrivacyBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NotificationPrivacyMode {
    FULL_CONTENT,
    SENDER_ONLY,
    HIDE_CONTENT,
    ;

    val wireValue: String get() = when (this) {
        FULL_CONTENT -> "full_content"
        SENDER_ONLY -> "sender_only"
        HIDE_CONTENT -> "hide_content"
    }

    companion object {
        fun fromWireValue(value: String): NotificationPrivacyMode = when (value) {
            "sender_only" -> SENDER_ONLY
            "hide_content" -> HIDE_CONTENT
            else -> FULL_CONTENT // matches the backend column's own DEFAULT — see 008_phase6_notifications.sql
        }
    }
}

sealed interface NotificationPrivacyUiState {
    data object Loading : NotificationPrivacyUiState
    data class Content(val selected: NotificationPrivacyMode, val isSaving: Boolean = false) : NotificationPrivacyUiState
    data class Error(val message: String) : NotificationPrivacyUiState
}

/**
 * Deliberately talks to ChatApi directly rather than going through
 * ChatRepository/Room — this one setting has no offline/local-first
 * story (see master-prompt section 36's fuller Privacy Center for where
 * that kind of investment would belong) and isn't referenced from
 * anywhere else in the app except NotificationsService's payload
 * building, which lives entirely server-side.
 */
@HiltViewModel
class NotificationPrivacyViewModel @Inject constructor(
    private val chatApi: ChatApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationPrivacyUiState>(NotificationPrivacyUiState.Loading)
    val uiState: StateFlow<NotificationPrivacyUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = NotificationPrivacyUiState.Loading
            try {
                val response = chatApi.getNotificationPrivacy()
                _uiState.value = NotificationPrivacyUiState.Content(NotificationPrivacyMode.fromWireValue(response.mode))
            } catch (e: Exception) {
                _uiState.value = NotificationPrivacyUiState.Error(GENERIC_ERROR)
            }
        }
    }

    fun onSelect(mode: NotificationPrivacyMode) {
        val current = _uiState.value as? NotificationPrivacyUiState.Content ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(selected = mode, isSaving = true)
            try {
                chatApi.updateNotificationPrivacy(UpdateNotificationPrivacyBody(mode.wireValue))
                _uiState.value = NotificationPrivacyUiState.Content(mode, isSaving = false)
            } catch (e: Exception) {
                // Revert to whatever was actually saved server-side, not
                // the tapped-but-failed value — a stale "success" looking
                // UI would be worse than briefly bouncing back.
                _uiState.value = current.copy(isSaving = false)
            }
        }
    }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
    }
}
