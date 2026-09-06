package com.omidgame.mench.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.chat.domain.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<ConversationListUiState> = combine(
        chatRepository.observeConversations(),
        _isRefreshing,
    ) { conversations, refreshing ->
        when {
            refreshing && conversations.isEmpty() -> ConversationListUiState.Loading
            conversations.isEmpty() -> ConversationListUiState.Empty
            else -> ConversationListUiState.Content(conversations)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConversationListUiState.Loading)

    init {
        // Connection lifetime is tied to "signed in", not to this screen —
        // disconnecting in onCleared() here would have dropped the socket
        // every time the user merely navigated from the conversation list
        // to an open chat (a different screen, a different ViewModel
        // instance, but the same underlying singleton connection). There
        // is no sign-out UI yet in this phase to hook a disconnect into;
        // when one exists, that's where disconnectRealtime() belongs, not
        // here.
        chatRepository.connectRealtime()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            chatRepository.refreshConversations()
            _isRefreshing.value = false
        }
    }
}
