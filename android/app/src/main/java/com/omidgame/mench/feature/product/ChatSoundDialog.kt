package com.omidgame.mench.feature.product

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.omidgame.mench.R
import com.omidgame.mench.core.notifications.ChatVibration
import com.omidgame.mench.core.notifications.NotificationPolicy
import com.omidgame.mench.core.ui.components.MenchDialog

/**
 * Per-chat notification sound screen (Phase 1C leftover): silent switch, vibration pattern and a
 * system ringtone picker. Values live in [NotificationPolicy]; per-chat channels are rebuilt there.
 */
@Composable
fun ChatSoundDialog(
    conversationId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val policy = remember { NotificationPolicy(context.applicationContext) }
    var silent by remember { mutableStateOf(policy.chatSilent(conversationId)) }
    var vibration by remember { mutableStateOf(policy.chatVibration(conversationId)) }
    var soundUri by remember { mutableStateOf(policy.chatSoundUri(conversationId)) }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            soundUri = picked?.toString()
            policy.setChatSoundUri(conversationId, soundUri)
        }
    }

    fun soundTitle(): String {
        val uri = soundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return context.getString(R.string.chat_sound_default)
        return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
            ?: context.getString(R.string.chat_sound_default)
    }

    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_sound_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.chat_sound_silent), modifier = Modifier.weight(1f))
                    Switch(
                        checked = silent,
                        onCheckedChange = {
                            silent = it
                            policy.setChatSound(conversationId, it, vibration)
                        },
                    )
                }
                if (!silent) {
                    Text(
                        stringResource(R.string.chat_sound_ringtone),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.chat_sound_title))
                                soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                            }
                            runCatching { pickerLauncher.launch(intent) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(soundTitle(), maxLines = 1) }
                }
                Text(
                    stringResource(R.string.chat_sound_vibration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChatVibration.entries.forEach { option ->
                        FilterChip(
                            selected = vibration == option,
                            onClick = {
                                vibration = option
                                policy.setChatSound(conversationId, silent, option)
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            ChatVibration.DEFAULT -> R.string.chat_vib_default
                                            ChatVibration.SHORT -> R.string.chat_vib_short
                                            ChatVibration.LONG -> R.string.chat_vib_long
                                            ChatVibration.OFF -> R.string.chat_vib_off
                                        },
                                    ),
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.padding(0.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_sound_done)) } },
        dismissButton = {
            TextButton(
                onClick = {
                    silent = false; vibration = ChatVibration.DEFAULT; soundUri = null
                    policy.setChatSound(conversationId, false, ChatVibration.DEFAULT)
                    policy.setChatSoundUri(conversationId, null)
                },
            ) { Text(stringResource(R.string.chat_sound_reset)) }
        },
    )
}
