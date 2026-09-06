package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Conversation
import com.omidgame.mench.feature.chat.domain.Message

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isSyncing: Boolean = false,
    val isOtherTyping: Boolean = false,
    /** Non-null while the composer is in edit mode for this message — see ChatViewModel.onStartEdit/onCancelEdit. */
    val editingMessage: Message? = null,
    /** Every conversation the user has, for the forward destination picker — kept here rather than a separate screen since forwarding is a same-screen dialog (see ChatScreen's ForwardDialog). */
    val conversations: List<Conversation> = emptyList(),
    /** Whether THIS screen's conversation (not any entry in `conversations` generally) is a group — gates whether ChatScreen shows a sender-name label above each received bubble, which only makes sense when more than one other person can send here. */
    val isGroupConversation: Boolean = false,
    /** userId -> display name (or phone number if no display name), fetched once via ChatRepository.listMembers — see ChatViewModel's doc comment on why this isn't kept live. */
    val memberDisplayNames: Map<String, String> = emptyMap(),
    /** The top app bar's title — a group's title, or the other member's display name/phone for a direct conversation. Null only in the brief window before `conversations` has loaded. */
    val headerTitle: String? = null,
)
