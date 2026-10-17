package com.omidgame.mench.feature.contacts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.chat.domain.ChatResult
import com.omidgame.mench.feature.chat.domain.StartDirectConversationUseCase
import com.omidgame.mench.feature.contacts.domain.Contact
import com.omidgame.mench.feature.contacts.domain.ContactsRepository
import com.omidgame.mench.feature.contacts.domain.ContactsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val startDirectConversationUseCase: StartDirectConversationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    /**
     * One-off navigation, deliberately not folded into uiState: a
     * conversationId sitting in StateFlow would re-fire navigation on
     * every recomposition/process restore that re-reads the last state,
     * not just the one time it actually happened. A SharedFlow with no
     * replay only ever delivers to a collector that's live at the
     * moment it's emitted — exactly once, from the nav graph's
     * LaunchedEffect.
     */
    private val _navigateToConversation = MutableSharedFlow<String>()
    val navigateToConversation: SharedFlow<String> = _navigateToConversation

    /** Called by the screen once READ_CONTACTS is confirmed granted — this ViewModel never checks the permission itself, matching ChatScreen's mic-permission split of "Compose owns permission state, ViewModel owns the resulting data operation". */
    fun loadContacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = contactsRepository.syncContacts()) {
                is ContactsResult.Success -> _uiState.update {
                    it.copy(isLoading = false, contacts = result.value.sortedBy { c -> c.deviceContactName })
                }
                is ContactsResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onContactClick(contact: Contact) {
        viewModelScope.launch {
            _uiState.update { it.copy(startingConversationForUserId = contact.userId) }
            when (val result = startDirectConversationUseCase(contact.phoneE164)) {
                is ChatResult.Success -> {
                    _uiState.update { it.copy(startingConversationForUserId = null) }
                    _navigateToConversation.emit(result.value.id)
                }
                is ChatResult.Failure -> _uiState.update {
                    it.copy(startingConversationForUserId = null, errorMessage = "Couldn't start the conversation. Please try again.")
                }
            }
        }
    }
}
