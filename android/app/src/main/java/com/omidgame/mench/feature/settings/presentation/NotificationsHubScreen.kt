package com.omidgame.mench.feature.settings.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omidgame.mench.R
import com.omidgame.mench.core.notifications.NotificationCategory
import com.omidgame.mench.core.ui.settings.BannerKind
import com.omidgame.mench.core.ui.settings.InfoBanner
import com.omidgame.mench.core.ui.settings.PermissionRow
import com.omidgame.mench.core.ui.settings.SettingsRow
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.core.ui.settings.SwitchRow
import com.omidgame.mench.core.ui.settings.openAppSettings
import com.omidgame.mench.feature.settings.domain.NotificationType
import com.omidgame.mench.feature.settings.domain.PhoneVendor
import com.omidgame.mench.feature.settings.domain.VendorGuide

/**
 * Notifications settings in one place: master switch, per-type switches, system sound/vibration pages, preview
 * privacy, quiet hours (+ calls exception), per-chat overrides, delivery reliability and permissions.
 */
@Composable
fun NotificationsHubScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: NotificationsHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Permissions and battery state change outside the app, so re-read them whenever we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refresh()
        // Denied (possibly permanently): the system will not ask again, so take the user to the settings page.
        if (!granted) openNotificationSettings(context)
    }

    SettingsScaffold(title = stringResource(R.string.settings_notifications), onBack = onBack) {
        if (!state.systemNotificationsEnabled) {
            InfoBanner(
                text = stringResource(R.string.sx_n_disabled_banner),
                kind = BannerKind.WARNING,
                actionLabel = stringResource(R.string.sx_n_system_open),
                onAction = { openNotificationSettings(context) },
            )
        }

        SettingsSection(title = null) {
            SwitchRow(
                title = stringResource(R.string.sx_n_master),
                subtitle = stringResource(R.string.sx_n_master_sub),
                checked = state.masterEnabled,
                onCheckedChange = viewModel::setMaster,
            )
        }

        SettingsSection(title = stringResource(R.string.sx_n_types)) {
            NotificationType.entries.forEach { type ->
                val (title, subtitle) = typeText(type)
                SwitchRow(
                    title = stringResource(title),
                    subtitle = stringResource(subtitle),
                    checked = state.typeEnabled[type] ?: true,
                    enabled = state.masterEnabled,
                    onCheckedChange = { viewModel.setType(type, it) },
                )
            }
        }

        // Sound, vibration and importance can only be changed by the user in the system's channel page.
        SettingsSection(title = stringResource(R.string.sx_n_system_open)) {
            Text(
                stringResource(R.string.sx_n_system_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
            NotificationCategory.entries.forEach { category ->
                SettingsRow(
                    title = stringResource(category.nameRes),
                    onClick = { openChannelSettings(context, category.channelId) },
                )
            }
        }

        SettingsSection(title = null) {
            SettingsRow(
                title = stringResource(R.string.sx_n_privacy),
                subtitle = stringResource(R.string.sx_n_privacy_sub),
                onClick = onOpenPrivacy,
            )
        }

        SettingsSection(title = stringResource(R.string.sx_n_quiet)) {
            QuietHoursSection()
            SwitchRow(
                title = stringResource(R.string.sx_n_quiet_calls),
                subtitle = stringResource(R.string.sx_n_quiet_calls_sub),
                checked = state.allowCallsInQuiet,
                onCheckedChange = viewModel::setAllowCallsInQuiet,
            )
        }

        SettingsSection(title = stringResource(R.string.sx_n_overrides)) {
            if (state.overrides.isEmpty()) {
                Text(
                    stringResource(R.string.sx_n_overrides_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
            state.overrides.forEach { row ->
                val parts = buildList {
                    if (row.override.mutedUntil != null) add(stringResource(R.string.sx_n_override_muted))
                    if (row.override.silent) add(stringResource(R.string.sx_n_override_silent))
                    if (row.override.vibrationName != null) add(stringResource(R.string.sx_n_override_vibration))
                    if (row.override.hasCustomSound) add(stringResource(R.string.sx_n_override_sound))
                }
                SettingsRow(
                    title = row.name.ifBlank { stringResource(R.string.sx_hub_chat_fallback) },
                    subtitle = parts.joinToString(" · "),
                    trailing = {
                        TextButton(onClick = { viewModel.clearOverride(row.override.conversationId) }) {
                            Text(stringResource(R.string.sx_n_override_clear))
                        }
                    },
                )
            }
            if (state.overrides.size > 1) {
                SettingsRow(
                    title = stringResource(R.string.sx_n_override_clear_all),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = viewModel::clearAllOverrides,
                )
            }
        }

        SettingsSection(title = stringResource(R.string.sx_n_reliability)) {
            Text(
                stringResource(R.string.sx_n_reliability_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
            SettingsRow(
                title = stringResource(R.string.sx_n_service),
                subtitle = stringResource(if (state.serviceRunning) R.string.sx_n_service_running else R.string.sx_n_service_stopped),
            )
            SettingsRow(
                title = stringResource(R.string.sx_n_battery),
                subtitle = stringResource(if (state.batteryUnrestricted) R.string.sx_n_battery_ok else R.string.sx_n_battery_restricted),
                onClick = if (state.batteryUnrestricted) null else { { requestIgnoreBatteryOptimisations(context) } },
            )
            SettingsRow(
                title = stringResource(R.string.sx_n_autostart),
                subtitle = autoStartText(state.vendor),
                onClick = { openAutoStart(context, state.vendor) },
            )
            SettingsRow(title = stringResource(R.string.sx_n_test), onClick = viewModel::sendTest)
        }

        SettingsSection(title = stringResource(R.string.sx_n_perm_title)) {
            PermissionRow(
                title = stringResource(R.string.sx_n_perm_notifications),
                granted = state.systemNotificationsEnabled,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context)
                },
            )
            state.fullScreenIntentAllowed?.let { allowed ->
                PermissionRow(
                    title = stringResource(R.string.sx_n_perm_fullscreen),
                    granted = allowed,
                    onClick = { openFullScreenIntentSettings(context) },
                )
            }
        }
    }
}

private fun typeText(type: NotificationType): Pair<Int, Int> = when (type) {
    NotificationType.MESSAGES -> R.string.sx_n_type_messages to R.string.sx_n_type_messages_sub
    NotificationType.AFFECTION -> R.string.sx_n_type_affection to R.string.sx_n_type_affection_sub
    NotificationType.LETTERS -> R.string.sx_n_type_letters to R.string.sx_n_type_letters_sub
    NotificationType.REMINDERS -> R.string.sx_n_type_reminders to R.string.sx_n_type_reminders_sub
}

@Composable
private fun autoStartText(vendor: PhoneVendor): String = when (vendor) {
    PhoneVendor.OTHER -> stringResource(R.string.sx_n_autostart_generic)
    else -> stringResource(
        R.string.sx_n_autostart_open,
        stringResource(
            when (vendor) {
                PhoneVendor.XIAOMI -> R.string.sx_vendor_xiaomi
                PhoneVendor.SAMSUNG -> R.string.sx_vendor_samsung
                PhoneVendor.HUAWEI -> R.string.sx_vendor_huawei
                PhoneVendor.OPPO -> R.string.sx_vendor_oppo
                else -> R.string.sx_vendor_vivo
            },
        ),
    )
}

private fun newTask(i: Intent): Intent = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(newTask(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)))
    }.onFailure { openAppSettings(context) }
}

private fun openChannelSettings(context: Context, channelId: String) {
    runCatching {
        context.startActivity(
            newTask(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
            ),
        )
    }.onFailure { openNotificationSettings(context) }
}

private fun requestIgnoreBatteryOptimisations(context: Context) {
    runCatching {
        context.startActivity(newTask(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))))
    }.onFailure { openAppSettings(context) }
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        runCatching {
            context.startActivity(newTask(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))))
        }.onFailure { openAppSettings(context) }
    } else {
        openAppSettings(context)
    }
}

/** Tries the vendor's own auto-start pages in order; falls back to the app's system settings page. */
private fun openAutoStart(context: Context, vendor: PhoneVendor) {
    for ((pkg, cls) in VendorGuide.autoStartComponents(vendor)) {
        val ok = runCatching { context.startActivity(newTask(Intent().setComponent(ComponentName(pkg, cls)))) }.isSuccess
        if (ok) return
    }
    openAppSettings(context)
}
