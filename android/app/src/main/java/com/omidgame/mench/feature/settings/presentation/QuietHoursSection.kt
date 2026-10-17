package com.omidgame.mench.feature.settings.presentation

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.notifications.NotificationPolicy
import com.omidgame.mench.core.ui.theme.MenchDigits

/** Do-Not-Disturb schedule (quiet hours): notifications still arrive but make no sound. */
@Composable
fun QuietHoursSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val policy = remember { NotificationPolicy(context.applicationContext) }
    var enabled by remember { mutableStateOf(policy.quietHoursEnabled) }
    var start by remember { mutableIntStateOf(policy.quietStartMinutes) }
    var end by remember { mutableIntStateOf(policy.quietEndMinutes) }

    fun pick(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.quiet_hours_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; policy.quietHoursEnabled = it })
        }
        Text(
            stringResource(R.string.quiet_hours_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (enabled) {
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pick(start) { start = it; policy.quietStartMinutes = it } }) {
                    Text(stringResource(R.string.quiet_hours_from) + " " + MenchDigits.toPersian(fmt(start)))
                }
                OutlinedButton(onClick = { pick(end) { end = it; policy.quietEndMinutes = it } }) {
                    Text(stringResource(R.string.quiet_hours_to) + " " + MenchDigits.toPersian(fmt(end)))
                }
            }
        }
    }
}

private fun fmt(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
