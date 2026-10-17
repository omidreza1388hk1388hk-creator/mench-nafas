package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog

/** Phase 3A — small daily rituals and the manual "I'm coming" status (no location is ever read or sent). */

/** Estimated minutes offered by [ImComingDialog]. */
private val comingMinutes = listOf(10, 20, 30, 60)

/** The text of the mood line the partner sees, e.g. "On my way — about 20 min". */
internal fun imComingText(template: String, minutes: Int): String = template.replace("%d", minutes.toString())

/**
 * Pick roughly how long until you arrive. [onPick] receives the minutes; the caller sets it as the shared mood
 * (🚗), so the partner sees it in the «ما» header without any location being involved.
 */
@Composable
fun ImComingDialog(onDismiss: () -> Unit, onPick: (minutes: Int) -> Unit) {
    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        title = { Text("🚗  " + stringResource(R.string.coming_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.coming_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    comingMinutes.forEach { m ->
                        OutlinedButton(onClick = { onPick(m) }) { Text(m.toString()) }
                    }
                }
            }
        },
    )
}
