package com.omidgame.mench.feature.chat.presentation

sealed interface NewGroupUiState {
    data object Idle : NewGroupUiState
    data object Loading : NewGroupUiState
    data class Error(val message: String) : NewGroupUiState
    data class Created(val conversationId: String) : NewGroupUiState
}
