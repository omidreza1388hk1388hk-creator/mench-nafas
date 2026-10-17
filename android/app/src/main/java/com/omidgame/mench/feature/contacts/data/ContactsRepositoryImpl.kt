package com.omidgame.mench.feature.contacts.data

import android.content.Context
import com.omidgame.mench.R
import com.omidgame.mench.core.contacts.ContactSyncPrefs
import com.omidgame.mench.core.contacts.DeviceContactsReader
import com.omidgame.mench.core.network.LookupUsersBatchBody
import com.omidgame.mench.core.network.UsersApi
import com.omidgame.mench.feature.contacts.domain.Contact
import com.omidgame.mench.feature.contacts.domain.ContactsRepository
import com.omidgame.mench.feature.contacts.domain.ContactsResult
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * No local caching/persistence here (unlike, say, ChatRepositoryImpl's
 * Room-backed messages) — the device address book is already the local
 * source of truth and can change at any time outside this app's
 * control, so every call to [syncContacts] re-reads it fresh rather
 * than risking a stale cached match list. This screen is opened
 * occasionally, not polled, so the extra read cost is a non-issue.
 *
 * Contacts with no matching MENCH account are simply dropped, not shown
 * as an "invite" section — that needs an SMS/share-link flow this pass
 * doesn't build; the phone numbers are still right there in the device
 * address book for the user to invite manually in the meantime.
 */
class ContactsRepositoryImpl @Inject constructor(
    private val deviceContactsReader: DeviceContactsReader,
    private val usersApi: UsersApi,
    private val syncPrefs: ContactSyncPrefs,
    @ApplicationContext private val context: Context,
) : ContactsRepository {

    override suspend fun syncContacts(): ContactsResult<List<Contact>> {
        return try {
            // Privacy switch (Settings > Contact privacy): when off, nothing is read and nothing is sent.
            if (!syncPrefs.syncEnabled) {
                return ContactsResult.Failure(context.getString(R.string.contact_sync_off_message))
            }
            val deviceContacts = deviceContactsReader.readAll()
            if (deviceContacts.isEmpty()) {
                return ContactsResult.Success(emptyList())
            }

            val matchedByPhone = usersApi.lookupBatch(LookupUsersBatchBody(deviceContacts.map { it.phoneE164 }))
                .associateBy { it.phoneE164 }

            val contacts = deviceContacts.mapNotNull { deviceContact ->
                val match = matchedByPhone[deviceContact.phoneE164] ?: return@mapNotNull null
                Contact(
                    userId = match.id,
                    deviceContactName = deviceContact.name,
                    phoneE164 = match.phoneE164,
                    displayName = match.displayName,
                    username = match.username,
                    avatarUrl = match.avatarUrl,
                )
            }
            syncPrefs.lastSyncAtEpochMillis = System.currentTimeMillis()
            syncPrefs.lastMatchCount = contacts.size
            ContactsResult.Success(contacts)
        } catch (e: IOException) {
            ContactsResult.Failure(e.message)
        } catch (e: HttpException) {
            ContactsResult.Failure(e.message)
        }
    }
}
