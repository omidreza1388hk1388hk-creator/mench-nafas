package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R

/** Reads the contact the user picked in the system contact picker (no READ_CONTACTS permission needed: the picked URI is granted for this read). */
fun readPickedContact(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() to cursor.getString(1).orEmpty() else null
    }
}.getOrNull()

/** The system picker for "a contact with a phone number". */
fun contactPickIntent(): Intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

/** A shared contact inside a bubble: name, number, and Call / Save buttons (call opens the dialer; it never dials by itself). */
@Composable
fun ContactBubbleContent(name: String, phone: String) {
    val context = LocalContext.current
    Column(modifier = Modifier.widthIn(min = 200.dp, max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).background(LocalContentColor.current.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.trim().firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleMedium)
            }
            Column {
                Text(name, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(phone, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = 0.8f))
            }
        }
        Row {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text(stringResource(R.string.contact_call)) }
            TextButton(onClick = {
                val insert = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.NAME, name)
                    putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(insert) }
            }) { Text(stringResource(R.string.contact_save)) }
        }
    }
}
