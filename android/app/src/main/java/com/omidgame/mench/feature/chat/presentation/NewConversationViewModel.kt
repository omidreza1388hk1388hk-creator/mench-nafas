package com.omidgame.mench.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.chat.domain.ChatFailureReason
import com.omidgame.mench.feature.chat.domain.ChatResult
import com.omidgame.mench.feature.chat.domain.StartDirectConversationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewConversationViewModel @Inject constructor(
    private val startDirectConversationUseCase: StartDirectConversationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewConversationUiState>(NewConversationUiState.Idle)
    val uiState: StateFlow<NewConversationUiState> = _uiState.asStateFlow()

    fun onSubmitPhone(phoneE164: String) {
        _uiState.value = NewConversationUiState.Loading
        viewModelScope.launch {
            when (val result = startDirectConversationUseCase(phoneE164)) {
                is ChatResult.Success -> _uiState.value = NewConversationUiState.Started(result.value.id)
                is ChatResult.Failure -> _uiState.value = NewConversationUiState.Error(messageFor(result.reason))
            }
        }
    }

    private fun messageFor(reason: ChatFailureReason): String = when (reason) {
        ChatFailureReason.NETWORK_UNAVAILABLE -> "No connection. Check your internet and try again."
        ChatFailureReason.USER_NOT_FOUND -> "No MENCH user found with that phone number."
        ChatFailureReason.CANNOT_MESSAGE_SELF -> "You can't start a conversation with yourself."
        ChatFailureReason.INVALID_GROUP, ChatFailureReason.NOT_GROUP_OWNER, ChatFailureReason.UNKNOWN ->
            "Something went wrong. Please try again."
    }
}
