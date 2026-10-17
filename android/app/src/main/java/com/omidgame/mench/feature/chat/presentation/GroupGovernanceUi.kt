package com.omidgame.mench.feature.chat.presentation

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.feature.chat.domain.GroupInvite
import com.omidgame.mench.feature.chat.domain.GroupRules
import com.omidgame.mench.feature.chat.domain.GroupSettings

internal fun slowModeLabel(seconds: Int): Int = when (seconds) {
    0 -> R.string.slow_mode_off
    10 -> R.string.slow_mode_10s
    30 -> R.string.slow_mode_30s
    60 -> R.string.slow_mode_1m
    300 -> R.string.slow_mode_5m
    900 -> R.string.slow_mode_15m
    else -> R.string.slow_mode_1h
}

/** Admin-only block of the Group Info screen: permissions, channel mode, slow mode and invite links. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GovernanceSection(
    settings: GroupSettings,
    isOwner: Boolean,
    invites: List<GroupInvite>,
    groupTitle: String,
    onSettingsChanged: (GroupSettings) -> Unit,
    onCreateInvite: (expiresInHours: Int?, maxUses: Int?) -> Unit,
    onRevokeInvite: (GroupInvite) -> Unit,
) {
    val context = LocalContext.current
    var expiryChoice by remember { mutableStateOf<Int?>(24) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.group_permissions_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        SwitchRow(R.string.group_only_admins_post, settings.adminsOnlySend) { onSettingsChanged(settings.copy(adminsOnlySend = it)) }
        if (settings.adminsOnlySend) {
            Text(stringResource(R.string.group_channel_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SwitchRow(R.string.group_only_admins_add, settings.adminsOnlyAddMembers) { onSettingsChanged(settings.copy(adminsOnlyAddMembers = it)) }
        SwitchRow(R.string.group_only_admins_edit, settings.adminsOnlyEditInfo) { onSettingsChanged(settings.copy(adminsOnlyEditInfo = it)) }
        SwitchRow(R.string.group_only_admins_pin, settings.adminsOnlyPin) { onSettingsChanged(settings.copy(adminsOnlyPin = it)) }

        Text(stringResource(R.string.slow_mode_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.slow_mode_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupRules.SLOW_MODE_OPTIONS.forEach { seconds ->
                FilterChip(
                    selected = settings.slowModeSeconds == seconds,
                    onClick = { onSettingsChanged(settings.copy(slowModeSeconds = seconds)) },
                    label = { Text(stringResource(slowModeLabel(seconds))) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.group_invites_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupRules.EXPIRY_PRESET_HOURS.forEach { hours ->
                FilterChip(
                    selected = expiryChoice == hours,
                    onClick = { expiryChoice = hours },
                    label = {
                        Text(
                            when (hours) {
                                1 -> stringResource(R.string.invite_expiry_1h)
                                24 -> stringResource(R.string.invite_expiry_1d)
                                24 * 7 -> stringResource(R.string.invite_expiry_7d)
                                else -> stringResource(R.string.invite_expiry_never)
                            },
                        )
                    },
                )
            }
        }
        OutlinedButton(onClick = { onCreateInvite(expiryChoice, null) }) { Text(stringResource(R.string.group_invite_create)) }
        invites.forEach { invite ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(invite.code.chunked(4).joinToString("-"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = (invite.expiresAtEpochMillis?.let { stringResource(R.string.invite_expires_at, formatWhen(it)) } ?: stringResource(R.string.invite_never_expires)) +
                            " · " + pluralStringResource(R.plurals.invite_uses, invite.uses, invite.uses),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, GroupRules.inviteShareText(groupTitle, invite.code))
                    }
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.group_invite_share)) }
                IconButton(onClick = { onRevokeInvite(invite) }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.group_invite_revoke)) }
            }
        }
        if (!isOwner) {
            Text(stringResource(R.string.group_admin_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
