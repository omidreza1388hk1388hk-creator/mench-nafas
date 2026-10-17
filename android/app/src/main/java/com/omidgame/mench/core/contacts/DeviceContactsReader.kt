package com.omidgame.mench.core.contacts

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceContact(
    val name: String,
    /** Best-effort E.164 — see normalize() below for exactly what this does and doesn't handle. */
    val phoneE164: String,
)

/**
 * Deliberately NOT full phone-number normalization (no libphonenumber
 * dependency pulled in for this) — it strips formatting characters and
 * accepts a number as-is if it already starts with "+", which covers the
 * common case (most phones store synced/SIM contacts with a country
 * code already). A local-format number with no country code (e.g. a
 * contact saved as "0912...") is skipped rather than guessed at, since
 * guessing the wrong country code would silently match it to a
 * different real person's account. This is a known, intentional scope
 * limit, not an oversight.
 */
@Singleton
class DeviceContactsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun readAll(): List<DeviceContact> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DeviceContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                val rawNumber = cursor.getString(numberIndex) ?: continue
                val normalized = normalize(rawNumber) ?: continue
                results += DeviceContact(name, normalized)
            }
        }
        // Same person often has multiple numbers/entries in the address
        // book (mobile + home, or duplicate sync sources) — de-duplicate
        // by the normalized number so the batch lookup call and the
        // resulting list don't carry redundant entries.
        results.distinctBy { it.phoneE164 }
    }

    private fun normalize(raw: String): String? {
        val stripped = raw.filter { it.isDigit() || it == '+' }
        return stripped.takeIf { it.startsWith("+") && it.length >= 8 }
    }
}
