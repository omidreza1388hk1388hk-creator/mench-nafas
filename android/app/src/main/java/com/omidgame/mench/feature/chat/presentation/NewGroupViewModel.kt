package com.omidgame.mench.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.chat.domain.ChatFailureReason
import com.omidgame.mench.feature.chat.domain.ChatResult
import com.omidgame.mench.feature.chat.domain.CreateGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewGroupViewModel @Inject constructor(
    private val createGroupUseCase: CreateGroupUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewGroupUiState>(NewGroupUiState.Idle)
    val uiState: StateFlow<NewGroupUiState> = _uiState.asStateFlow()

    fun onSubmit(title: String, memberPhoneNumbers: List<String>) {
        _uiState.value = NewGroupUiState.Loading
        viewModelScope.launch {
            when (val result = createGroupUseCase(title, memberPhoneNumbers)) {
                is ChatResult.Success -> _uiState.value = NewGroupUiState.Created(result.value.id)
                is ChatResult.Failure -> _uiState.value = NewGroupUiState.Error(messageFor(result.reason))
            }
        }
    }

    private fun messageFor(reason: ChatFailureReason): String = when (reason) {
        ChatFailureReason.NETWORK_UNAVAILABLE -> "No connection. Check your internet and try again."
        ChatFailureReason.USER_NOT_FOUND -> "One of those phone numbers isn't a MENCH user."
        ChatFailureReason.INVALID_GROUP -> "Enter a group name and at least one member."
        ChatFailureReason.CANNOT_MESSAGE_SELF, ChatFailureReason.NOT_GROUP_OWNER, ChatFailureReason.UNKNOWN ->
            "Something went wrong. Please try again."
    }
}
