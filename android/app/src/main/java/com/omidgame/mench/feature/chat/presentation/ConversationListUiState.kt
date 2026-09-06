package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Conversation

sealed interface ConversationListUiState {
    data object Loading : ConversationListUiState
    data object Empty : ConversationListUiState
    data class Content(val conversations: List<Conversation>) : ConversationListUiState
    data class Error(val message: String) : ConversationListUiState
}
