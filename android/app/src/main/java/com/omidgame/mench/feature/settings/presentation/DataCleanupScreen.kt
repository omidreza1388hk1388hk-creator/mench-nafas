package com.omidgame.mench.feature.settings.presentation

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omidgame.mench.R
import com.omidgame.mench.core.backup.BackupStatusStore
import com.omidgame.mench.core.media.CacheHygiene
import com.omidgame.mench.core.ui.settings.ChoiceOption
import com.omidgame.mench.core.ui.settings.SettingsRow
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.core.ui.settings.SingleChoiceRow
import com.omidgame.mench.core.ui.theme.MenchCalendar
import com.omidgame.mench.feature.settings.domain.CacheHygienePolicy

/** Data & Storage extras: temporary-file hygiene, plus read-only shortcuts to backup status and data usage. */
@Composable
fun DataCleanupScreen(onBack: () -> Unit, onOpenDataUsage: () -> Unit, onOpenSecurityHub: () -> Unit) {
    val context = LocalContext.current
    var keepDays by remember { mutableStateOf(CacheHygiene.keepDays(context)) }
    var size by remember { mutableStateOf(CacheHygiene.sizeBytes(context)) }
    val cleared = stringResource(R.string.sx_dc_cleared)
    val last = BackupStatusStore.lastBackupAt(context)

    SettingsScaffold(title = stringResource(R.string.sx_dc_title), onBack = onBack) {
        SettingsSection(title = stringResource(R.string.sx_dc_temp)) {
            SingleChoiceRow(
                title = stringResource(R.string.sx_dc_keep),
                subtitle = stringResource(R.string.sx_dc_keep_sub),
                options = CacheHygienePolicy.KEEP_DAYS_CHOICES.map { d -> ChoiceOption(d, stringResource(R.string.sx_dc_days, d)) },
                selected = keepDays,
                onSelected = { keepDays = it; CacheHygiene.setKeepDays(context, it); CacheHygiene.cleanExpired(context); size = CacheHygiene.sizeBytes(context) },
            )
            SettingsRow(
                title = stringResource(R.string.sx_dc_clear_edits),
                subtitle = stringResource(R.string.sx_dc_clear_edits_sub, Formatter.formatShortFileSize(context, size)),
                onClick = if (size > 0) ({
                    CacheHygiene.clearAll(context)
                    size = CacheHygiene.sizeBytes(context)
                    Toast.makeText(context, cleared, Toast.LENGTH_SHORT).show()
                }) else null,
            )
        }
        SettingsSection(title = stringResource(R.string.sx_dc_shortcuts)) {
            SettingsRow(
                title = stringResource(R.string.sx_dc_backup_status),
                subtitle = if (last == null) stringResource(R.string.sx_hub_backup_never) else stringResource(R.string.sx_hub_backup_last, formatBackupDate(last)),
                onClick = onOpenSecurityHub,
            )
            SettingsRow(title = stringResource(R.string.usage_title), onClick = onOpenDataUsage)
        }
    }
}

private fun formatBackupDate(millis: Long): String =
    android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.MEDIUM, MenchCalendar.locale()).format(java.util.Date(millis))
