package com.omidgame.mench.feature.chat.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchSheet
import com.omidgame.mench.feature.chat.domain.ScheduledMessage
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

private fun optionLabel(key: ScheduleOption.Key): Int = when (key) {
    ScheduleOption.Key.IN_ONE_HOUR -> R.string.schedule_in_one_hour
    ScheduleOption.Key.TONIGHT -> R.string.schedule_tonight
    ScheduleOption.Key.TOMORROW_MORNING -> R.string.schedule_tomorrow_morning
    ScheduleOption.Key.TOMORROW_EVENING -> R.string.schedule_tomorrow_evening
}

internal fun formatWhen(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

/** Long-press on Send: send without sound, or pick when to send. */
@Composable
fun SendOptionsSheet(
    onSilent: () -> Unit,
    onScheduleAt: (epochMillis: Long, surprise: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val options = SchedulePolicy.quickOptions(System.currentTimeMillis(), zone)
    // Phase 3: a scheduled message can be a surprise: the receiver gets a gift to open instead of plain text.
    var surprise by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    fun pickCustom() {
        val today = LocalDate.now(zone)
        DatePickerDialog(context, { _, y, m, d ->
            val date = LocalDate.of(y, m + 1, d)
            TimePickerDialog(context, { _, h, min ->
                val at = SchedulePolicy.combine(date, LocalTime.of(h, min), zone)
                if (SchedulePolicy.isAcceptable(at, System.currentTimeMillis())) {
                    onScheduleAt(at, surprise)
                } else {
                    android.widget.Toast.makeText(context, R.string.schedule_time_invalid, android.widget.Toast.LENGTH_LONG).show()
                }
            }, 9, 0, android.text.format.DateFormat.is24HourFormat(context)).show()
        }, today.year, today.monthValue - 1, today.dayOfMonth).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
            datePicker.maxDate = System.currentTimeMillis() + SchedulePolicy.MAX_LEAD_MS - 24L * 60 * 60 * 1000
        }.show()
    }

    MenchSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            SheetRow(Icons.Filled.VolumeOff, stringResource(R.string.send_silently), null) {
                onSilent()
                onDismiss()
            }
            Text(
                stringResource(R.string.schedule_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("🎁  " + stringResource(R.string.surprise_toggle))
                androidx.compose.material3.Switch(checked = surprise, onCheckedChange = { surprise = it })
            }
            options.forEach { option ->
                SheetRow(Icons.Filled.Schedule, stringResource(optionLabel(option.labelKey)), formatWhen(option.atEpochMillis)) {
                    onScheduleAt(option.atEpochMillis, surprise)
                }
            }
            SheetRow(Icons.Filled.Schedule, stringResource(R.string.schedule_pick_time), null) { pickCustom() }
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pending scheduled messages of this chat, each with a cancel button. */
@Composable
fun ScheduledMessagesSheet(messages: List<ScheduledMessage>, onCancel: (ScheduledMessage) -> Unit, onDismiss: () -> Unit) {
    MenchSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.scheduled_messages_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (messages.isEmpty()) {
                Text(stringResource(R.string.scheduled_messages_empty), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                items(messages, key = { it.id }) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val preview = RichBody.kindOf(message.body)?.let { stringResource(richKindLabel(it)) } ?: message.body
                            Text(preview, maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                formatWhen(message.sendAtEpochMillis) + if (message.silent) " · " + stringResource(R.string.send_silently) else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onCancel(message) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.scheduled_cancel))
                        }
                    }
                }
            }
        }
    }
}
