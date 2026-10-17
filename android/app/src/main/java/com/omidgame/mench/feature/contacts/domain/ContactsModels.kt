package com.omidgame.mench.feature.contacts.domain

/** A device address-book entry that's matched to a registered MENCH account. Unmatched device contacts are simply omitted — see ContactsRepositoryImpl's doc comment for why this screen shows no "invite" list yet. */
data class Contact(
    val userId: String,
    val deviceContactName: String,
    val phoneE164: String,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String?,
)

sealed interface ContactsResult<out T> {
    data class Success<T>(val value: T) : ContactsResult<T>
    data class Failure(val message: String?) : ContactsResult<Nothing>
}

interface ContactsRepository {
    /** Reads the device address book, batch-looks-up every number against MENCH accounts, and returns only the matches — see DeviceContactsReader for the read side and ContactsRepositoryImpl for the matching. */
    suspend fun syncContacts(): ContactsResult<List<Contact>>
}
