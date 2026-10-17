package com.omidgame.mench.feature.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.chat.presentation.DraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatChoice(val id: String, val name: String)

data class ChatsSettingsUiState(
    val chats: List<ChatChoice> = emptyList(),
    val draftCount: Int = 0,
)

@HiltViewModel
class ChatsSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatsSettingsUiState())
    val state: StateFlow<ChatsSettingsUiState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val (chats, drafts) = withContext(Dispatchers.IO) {
                val list = runCatching { chatRepository.observeConversations().first() }.getOrDefault(emptyList())
                    .map { ChatChoice(it.id, it.title ?: it.otherUserDisplayName ?: it.otherUserPhoneE164.orEmpty()) }
                list to DraftStore.count(context)
            }
            _state.update { it.copy(chats = chats, draftCount = drafts) }
        }
    }

    fun clearDrafts() {
        DraftStore.clearAll(context)
        refresh()
    }
}
