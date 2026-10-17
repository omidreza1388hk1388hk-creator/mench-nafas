package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Conversation

sealed interface ConversationListUiState {
    data object Loading : ConversationListUiState
    data object Empty : ConversationListUiState
    data class Content(
        val conversations: List<Conversation>,
        val archivedCount: Int = 0,
        val showingArchived: Boolean = false,
        val previews: Map<String, com.omidgame.mench.feature.chat.domain.ConversationPreview> = emptyMap(),
        /** Phase 2A: all of this phone's folders, the active filter and the chats manually marked unread. */
        val folders: List<ChatFolder> = emptyList(),
        val filter: ChatFilter = ChatFilter.All,
        val manualUnread: Set<String> = emptySet(),
        /** Unread chats / group chats among the (non-archived) list, for the filter chips' counters. */
        val unreadChatCount: Int = 0,
        val hasGroups: Boolean = false,
        /** Conversations whose other member is typing right now (shown in the row instead of the last message). */
        val typingIds: Set<String> = emptySet(),
    ) : ConversationListUiState
    data class Error(val message: String) : ConversationListUiState
}
