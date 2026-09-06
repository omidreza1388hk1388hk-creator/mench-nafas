package com.omidgame.mench.feature.chat.presentation

sealed interface NewConversationUiState {
    data object Idle : NewConversationUiState
    data object Loading : NewConversationUiState
    data class Error(val message: String) : NewConversationUiState
    data class Started(val conversationId: String) : NewConversationUiState
}
