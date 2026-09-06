package com.omidgame.mench.feature.chat.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.chat.domain.ChatFailureReason
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.chat.domain.ChatResult
import com.omidgame.mench.feature.chat.domain.GroupChangeSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _uiState = MutableStateFlow<GroupInfoUiState>(GroupInfoUiState.Loading)
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    init {
        load()

        // Phase 6: keep an already-open Group Info screen live when
        // ANOTHER member adds/removes someone or renames the group —
        // before this, the screen only ever refreshed after the
        // signed-in user's own action (see the doc comment on load()).
        viewModelScope.launch {
            chatRepository.observeGroupChanges().collect { signal ->
                if (signal.conversationId != conversationId) return@collect
                when (signal) {
                    is GroupChangeSignal.SelfRemoved -> _uiState.value = GroupInfoUiState.Left
                    is GroupChangeSignal.MembershipChanged -> load()
                    is GroupChangeSignal.Renamed -> {
                        // Cheap in-place update instead of a full load() —
                        // renames don't need a fresh member-list fetch,
                        // just the title. If a reload is already in flight
                        // from another signal, that reload's own copy of
                        // the (now-current) title will simply overwrite
                        // this anyway, so there's no ordering hazard.
                        (_uiState.value as? GroupInfoUiState.Content)?.let {
                            _uiState.value = it.copy(title = signal.title)
                        }
                    }
                }
            }
        }
    }

    /**
     * Re-fetched from the network every time (members, not cached in
     * Room — see ChatRepository.listMembers) rather than patched
     * in-memory after every add/remove — a group's own roster is small
     * enough that a full reload is cheap, and it's the simplest way to
     * stay consistent with whatever the server just did (e.g. an add
     * that partially failed server-side).
     */
    fun load() {
        viewModelScope.launch {
            _uiState.value = GroupInfoUiState.Loading
            val selfId = chatRepository.currentUserId()
            val conversation = chatRepository.observeConversations().firstOrNull { it.id == conversationId }
            when (val result = chatRepository.listMembers(conversationId)) {
                is ChatResult.Success -> {
                    val members = result.value
                    val isOwner = members.any { it.userId == selfId && it.isOwner }
                    _uiState.value = GroupInfoUiState.Content(
                        title = conversation?.title ?: "",
                        members = members,
                        selfUserId = selfId,
                        isOwner = isOwner,
                    )
                }
                is ChatResult.Failure -> _uiState.value = GroupInfoUiState.Error(messageFor(result.reason))
            }
        }
    }

    fun onRename(newTitle: String) {
        val current = _uiState.value as? GroupInfoUiState.Content ?: return
        viewModelScope.launch {
            when (val result = chatRepository.renameGroup(conversationId, newTitle)) {
                is ChatResult.Success -> _uiState.value = current.copy(title = newTitle, actionError = null)
                is ChatResult.Failure -> _uiState.value = current.copy(actionError = messageFor(result.reason))
            }
        }
    }

    fun onAddMember(phoneE164: String) {
        val current = _uiState.value as? GroupInfoUiState.Content ?: return
        viewModelScope.launch {
            when (val result = chatRepository.addMembers(conversationId, listOf(phoneE164))) {
                is ChatResult.Success -> load()
                is ChatResult.Failure -> _uiState.value = current.copy(actionError = messageFor(result.reason))
            }
        }
    }

    /** Removing your own userId is "leave group" — see ChatRepository.removeMember's doc comment. Transitions to GroupInfoUiState.Left so the nav graph can pop back to the conversation list. */
    fun onRemoveMember(userId: String) {
        val current = _uiState.value as? GroupInfoUiState.Content ?: return
        viewModelScope.launch {
            when (val result = chatRepository.removeMember(conversationId, userId)) {
                is ChatResult.Success -> {
                    if (userId == current.selfUserId) {
                        _uiState.value = GroupInfoUiState.Left
                    } else {
                        load()
                    }
                }
                is ChatResult.Failure -> _uiState.value = current.copy(actionError = messageFor(result.reason))
            }
        }
    }

    private fun messageFor(reason: ChatFailureReason): String = when (reason) {
        ChatFailureReason.NETWORK_UNAVAILABLE -> "No connection. Check your internet and try again."
        ChatFailureReason.USER_NOT_FOUND -> "That phone number isn't a MENCH user."
        ChatFailureReason.NOT_GROUP_OWNER -> "Only the group owner can do that."
        ChatFailureReason.INVALID_GROUP, ChatFailureReason.CANNOT_MESSAGE_SELF, ChatFailureReason.UNKNOWN ->
            "Something went wrong. Please try again."
    }
}
