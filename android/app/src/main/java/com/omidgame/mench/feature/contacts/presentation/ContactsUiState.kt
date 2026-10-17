package com.omidgame.mench.feature.contacts.presentation

import com.omidgame.mench.feature.contacts.domain.Contact

data class ContactsUiState(
    val isLoading: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null,
    /** Set while StartDirectConversationUseCase is resolving a tapped contact into a conversation to navigate into — see ContactsViewModel.onContactClick. */
    val startingConversationForUserId: String? = null,
) {
    /** Filtered, not the raw list — ContactsScreen renders this directly rather than filtering in the composable, keeping that filtering logic in one place and testable. */
    val filteredContacts: List<Contact>
        get() = if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.deviceContactName.contains(searchQuery, ignoreCase = true) ||
                    it.displayName?.contains(searchQuery, ignoreCase = true) == true
            }
        }
}
