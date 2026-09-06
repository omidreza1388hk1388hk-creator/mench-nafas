package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.ConversationMember

sealed interface GroupInfoUiState {
    data object Loading : GroupInfoUiState
    data class Content(
        val title: String,
        val members: List<ConversationMember>,
        val selfUserId: String?,
        val isOwner: Boolean,
        /** Set after a failed add/remove/rename action — cleared on the next attempt, not auto-dismissed on a timer (this is a small settings-style screen, not a snackbar-driven one). */
        val actionError: String? = null,
    ) : GroupInfoUiState
    data class Error(val message: String) : GroupInfoUiState
    /** Terminal state after successfully leaving the group — the nav graph pops back to the conversation list on seeing this (see MenchNavGraph). */
    data object Left : GroupInfoUiState
}
