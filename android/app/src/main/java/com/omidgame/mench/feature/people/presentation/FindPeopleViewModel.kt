package com.omidgame.mench.feature.people.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.chat.domain.ChatResult
import com.omidgame.mench.feature.people.data.PeopleRepository
import com.omidgame.mench.feature.people.data.PeopleResult
import com.omidgame.mench.feature.people.data.PublicUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FindMessage { NOT_FOUND, OFFLINE, FAILED, BLOCKED, START_FAILED }

data class FindPeopleUiState(
    val usernameQuery: String = "",
    val inviteInput: String = "",
    val isSearching: Boolean = false,
    val result: PublicUser? = null,
    val message: FindMessage? = null,
    val myInviteCode: String? = null,
    val isStartingChat: Boolean = false,
)

@HiltViewModel
class FindPeopleViewModel @Inject constructor(
    private val people: PeopleRepository,
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindPeopleUiState())
    val uiState: StateFlow<FindPeopleUiState> = _uiState.asStateFlow()

    private val _openChat = Channel<String>(Channel.BUFFERED)
    val openChat: Flow<String> = _openChat.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (val r = people.myInviteCode()) {
                is PeopleResult.Success -> _uiState.update { it.copy(myInviteCode = r.value) }
                else -> Unit
            }
        }
        // Arrives from a mench://invite/<code> deep link (see MenchNavGraph's FIND_PEOPLE route).
        val deepLinked: String? = savedStateHandle["invite"]
        if (!deepLinked.isNullOrBlank()) {
            _uiState.update { it.copy(inviteInput = deepLinked) }
            onResolveInvite()
        }
    }

    fun onUsernameChange(value: String) =
        _uiState.update { it.copy(usernameQuery = value, message = null) }

    fun onInviteInputChange(value: String) =
        _uiState.update { it.copy(inviteInput = value, message = null) }

    fun onSearchUsername() {
        val query = _uiState.value.usernameQuery
        if (query.isBlank() || _uiState.value.isSearching) return
        _uiState.update { it.copy(isSearching = true, message = null, result = null) }
        viewModelScope.launch { applyLookup(people.findByUsername(query)) }
    }

    fun onResolveInvite() {
        val input = _uiState.value.inviteInput
        if (input.isBlank() || _uiState.value.isSearching) return
        _uiState.update { it.copy(isSearching = true, message = null, result = null) }
        viewModelScope.launch { applyLookup(people.resolveInvite(input)) }
    }

    private fun applyLookup(result: PeopleResult<PublicUser>) {
        _uiState.update {
            when (result) {
                is PeopleResult.Success -> it.copy(isSearching = false, result = result.value)
                PeopleResult.NotFound -> it.copy(isSearching = false, message = FindMessage.NOT_FOUND)
                PeopleResult.Offline -> it.copy(isSearching = false, message = FindMessage.OFFLINE)
                PeopleResult.Failed -> it.copy(isSearching = false, message = FindMessage.FAILED)
            }
        }
    }

    fun onMessageClicked() {
        val user = _uiState.value.result ?: return
        if (_uiState.value.isStartingChat) return
        _uiState.update { it.copy(isStartingChat = true, message = null) }
        viewModelScope.launch {
            when (val r = chatRepository.startDirectConversationWithUser(user.id)) {
                is ChatResult.Success -> {
                    _uiState.update { it.copy(isStartingChat = false) }
                    _openChat.send(r.value.id)
                }
                is ChatResult.Failure ->
                    _uiState.update { it.copy(isStartingChat = false, message = FindMessage.START_FAILED) }
            }
        }
    }

    fun onBlockClicked() {
        val user = _uiState.value.result ?: return
        viewModelScope.launch {
            when (people.block(user.id)) {
                is PeopleResult.Success -> _uiState.update { it.copy(result = null, message = FindMessage.BLOCKED) }
                PeopleResult.Offline -> _uiState.update { it.copy(message = FindMessage.OFFLINE) }
                else -> _uiState.update { it.copy(message = FindMessage.FAILED) }
            }
        }
    }
}
