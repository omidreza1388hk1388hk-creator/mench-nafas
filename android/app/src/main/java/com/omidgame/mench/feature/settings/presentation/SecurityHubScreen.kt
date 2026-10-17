package com.omidgame.mench.feature.settings.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omidgame.mench.R
import com.omidgame.mench.core.crypto.E2eeManager
import com.omidgame.mench.core.crypto.SecurityCode
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.core.ui.settings.BannerKind
import com.omidgame.mench.core.ui.settings.ChoiceOption
import com.omidgame.mench.core.ui.settings.InfoBanner
import com.omidgame.mench.core.ui.settings.SettingsRow
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.core.ui.settings.SingleChoiceRow
import com.omidgame.mench.core.ui.settings.SwitchRow
import com.omidgame.mench.core.ui.theme.MenchCalendar
import com.omidgame.mench.feature.product.QrCodes
import com.omidgame.mench.feature.settings.domain.BackupReminder
import com.omidgame.mench.feature.settings.domain.ChatSecurityRow
import com.omidgame.mench.feature.settings.domain.SecurityCheckItem

/**
 * One place for everything sensitive: checkup with one-tap fixes, encrypted and locked chats, security codes,
 * backup status and reminder. Existing screens (app lock, two-step, devices, blocked) are linked, not rebuilt.
 */
@Composable
fun SecurityHubScreen(
    onBack: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenTwoStep: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenBlocked: () -> Unit,
    viewModel: SecurityHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Coming back from app lock / devices / backup screens must refresh the checkup.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    var codeFor by remember { mutableStateOf<ChatSecurityRow?>(null) }

    SettingsScaffold(title = stringResource(R.string.sx_hub_title), onBack = onBack) {
        if (state.showNoBackupBanner) {
            InfoBanner(text = stringResource(R.string.sx_hub_backup_banner), kind = BannerKind.WARNING)
        }
        if (state.failedChatId != null && state.secureChats.none { it.conversationId == state.failedChatId }) {
            InfoBanner(text = stringResource(R.string.sx_hub_e2ee_failed), kind = BannerKind.DANGER)
        }
        if (state.reminderDue && !state.showNoBackupBanner) {
            InfoBanner(text = stringResource(R.string.sx_hub_backup_due), kind = BannerKind.INFO)
        }

        // ---- Checkup ----
        SettingsSection(title = stringResource(R.string.sx_checkup_title)) {
            val result = state.checkup
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.sx_checkup_score, result.score, result.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                LinearProgressIndicator(progress = { result.score.toFloat() / result.total }, modifier = Modifier.fillMaxWidth())
                if (result.isPerfect) Text(stringResource(R.string.sx_checkup_perfect), style = MaterialTheme.typography.bodyMedium)
            }
            result.missing.forEach { item ->
                SettingsRow(
                    title = stringResource(checkItemTitle(item)),
                    trailing = {
                        TextButton(
                            onClick = {
                                when (item) {
                                    SecurityCheckItem.SCREEN_LOCK -> runCatching {
                                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                    SecurityCheckItem.APP_LOCK -> onOpenAppLock()
                                    SecurityCheckItem.TWO_STEP -> onOpenTwoStep()
                                    SecurityCheckItem.BACKUP -> Unit // the backup section is on this same screen
                                    SecurityCheckItem.DEVICES -> { viewModel.markDevicesReviewed(); onOpenDevices() }
                                    SecurityCheckItem.E2EE_MAIN_CHAT -> viewModel.enableMainChatE2ee()
                                }
                            },
                        ) { Text(stringResource(R.string.sx_check_fix)) }
                    },
                )
            }
        }

        // ---- Links ----
        SettingsSection(title = stringResource(R.string.sx_hub_links)) {
            SettingsRow(title = stringResource(R.string.sx_hub_link_applock), onClick = onOpenAppLock)
            SettingsRow(title = stringResource(R.string.two_step_title), onClick = onOpenTwoStep)
            SettingsRow(title = stringResource(R.string.devices_title), onClick = { viewModel.markDevicesReviewed(); onOpenDevices() })
            SettingsRow(title = stringResource(R.string.sx_hub_link_blocked), onClick = onOpenBlocked)
        }

        // ---- Encrypted chats ----
        SettingsSection(title = stringResource(R.string.sx_hub_e2ee_title)) {
            if (state.secureChats.isEmpty()) {
                Text(
                    stringResource(R.string.sx_hub_e2ee_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
            state.secureChats.forEach { row ->
                val name = row.name.ifBlank { stringResource(R.string.sx_hub_chat_fallback) }
                SwitchRow(
                    title = name,
                    subtitle = stringResource(if (row.keyChangePending) R.string.sx_hub_key_changed else R.string.sx_hub_e2ee_on_sub),
                    checked = true,
                    enabled = state.busyChatId != row.conversationId,
                    onCheckedChange = { viewModel.setChatSecure(row, false) },
                )
                if (state.failedChatId == row.conversationId) {
                    InfoBanner(text = stringResource(R.string.sx_hub_e2ee_failed), kind = BannerKind.DANGER, modifier = Modifier.padding(horizontal = 10.dp))
                }
                SettingsRow(title = stringResource(R.string.sx_keychange_view), onClick = { codeFor = row })
                if (row.keyChangePending) {
                    SettingsRow(
                        title = stringResource(R.string.sx_keychange_ack),
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { viewModel.acknowledgeKeyChange(row) },
                    )
                }
            }
        }

        // ---- Locked chats ----
        SettingsSection(title = stringResource(R.string.sx_hub_locked_title)) {
            if (state.lockedChats.isEmpty()) {
                Text(
                    stringResource(R.string.sx_hub_locked_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
            state.lockedChats.forEach { row ->
                SwitchRow(
                    title = row.name.ifBlank { stringResource(R.string.sx_hub_chat_fallback) },
                    subtitle = stringResource(R.string.sx_hub_locked_sub),
                    checked = true,
                    onCheckedChange = { viewModel.unlockChat(row) },
                )
            }
        }

        // ---- Backup ----
        SettingsSection(title = stringResource(R.string.sx_hub_backup_title)) {
            val last = state.lastBackupAt
            SettingsRow(
                title = if (last == null) stringResource(R.string.sx_hub_backup_never) else stringResource(R.string.sx_hub_backup_last, formatDate(last)),
            )
            SingleChoiceRow(
                title = stringResource(R.string.sx_hub_backup_reminder),
                options = listOf(
                    ChoiceOption(BackupReminder.NEVER, stringResource(R.string.sx_remind_never)),
                    ChoiceOption(BackupReminder.WEEKLY, stringResource(R.string.sx_remind_weekly)),
                    ChoiceOption(BackupReminder.MONTHLY, stringResource(R.string.sx_remind_monthly)),
                ),
                selected = state.reminder,
                onSelected = viewModel::setReminder,
            )
        }
        BackupSection()
    }

    codeFor?.let { row -> SecurityCodeDialog(row = row, onDismiss = { codeFor = null }) }
}

private fun checkItemTitle(item: SecurityCheckItem): Int = when (item) {
    SecurityCheckItem.SCREEN_LOCK -> R.string.sx_check_screen_lock
    SecurityCheckItem.APP_LOCK -> R.string.sx_check_app_lock
    SecurityCheckItem.TWO_STEP -> R.string.sx_check_two_step
    SecurityCheckItem.BACKUP -> R.string.sx_check_backup
    SecurityCheckItem.DEVICES -> R.string.sx_check_devices
    SecurityCheckItem.E2EE_MAIN_CHAT -> R.string.sx_check_e2ee
}

/** Date in the user's calendar and digit style (Jalali by default), via the app's MenchCalendar locale. */
private fun formatDate(millis: Long): String =
    android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.MEDIUM, MenchCalendar.locale())
        .format(java.util.Date(millis))

/** Read-only: my key code, the partner key code, the shared 60-digit code and a QR of it. Public keys only. */
@Composable
private fun SecurityCodeDialog(row: ChatSecurityRow, onDismiss: () -> Unit) {
    val myKey = remember { E2eeManager.publicKey() }
    val peerKey = remember(row.peerUserId) { row.peerUserId?.let { E2eeManager.cachedPeerKey(it) } }
    val ltr = TextStyle(textDirection = TextDirection.Ltr, fontFamily = FontFamily.Monospace)
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.name.ifBlank { stringResource(R.string.sx_hub_chat_fallback) }) },
        text = {
            if (myKey == null || peerKey == null) {
                Text(stringResource(R.string.sx_hub_no_key))
            } else {
                val pair = remember(myKey, peerKey) { SecurityCode.forPair(myKey, peerKey) }
                val qr = remember(myKey, peerKey) { QrCodes.generate(SecurityCode.qrPayload(myKey, peerKey), 480).asImageBitmap() }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.sx_hub_pair_code), style = MaterialTheme.typography.titleSmall)
                    Text(pair, style = ltr, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Image(qr, contentDescription = stringResource(R.string.sx_hub_qr_desc), modifier = Modifier.size(200.dp))
                    Text(stringResource(R.string.sx_hub_my_key), style = MaterialTheme.typography.labelLarge)
                    Text(SecurityCode.forKey(myKey), style = ltr, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.sx_hub_partner_key), style = MaterialTheme.typography.labelLarge)
                    Text(SecurityCode.forKey(peerKey), style = ltr, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.sx_seccode_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.seen_by_close)) } },
    )
}
