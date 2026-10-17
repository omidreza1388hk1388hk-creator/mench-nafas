package com.omidgame.mench.feature.privacy.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.a11y.minTouchTarget
import com.omidgame.mench.feature.privacy.domain.DisappearingPresets

@Composable
fun disappearingLabel(seconds: Int?): String = when (seconds ?: DisappearingPresets.OFF) {
    DisappearingPresets.OFF -> stringResource(R.string.disappearing_off)
    DisappearingPresets.ONE_HOUR -> stringResource(R.string.disappearing_1h)
    DisappearingPresets.ONE_DAY -> stringResource(R.string.disappearing_24h)
    DisappearingPresets.ONE_WEEK -> stringResource(R.string.disappearing_7d)
    DisappearingPresets.THIRTY_DAYS -> stringResource(R.string.disappearing_30d)
    else -> stringResource(R.string.disappearing_custom)
}

/** Thin strip shown under the chat's top bar while a timer is active. */
@Composable
fun DisappearingBanner(seconds: Int?, modifier: Modifier = Modifier) {
    if (seconds == null || seconds == 0) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.disappearing_banner, disappearingLabel(seconds)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun DisappearingDialog(
    state: DisappearingUiState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = state.seconds ?: DisappearingPresets.OFF
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disappearing_title)) },
        text = {
            Column {
                Text(stringResource(R.string.disappearing_explainer), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(0.dp))
                DisappearingPresets.all.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minTouchTarget()
                            .selectable(selected = current == preset, role = Role.RadioButton, enabled = !state.isSaving) { onSelect(preset) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == preset, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(disappearingLabel(preset))
                    }
                }
                if (!DisappearingPresets.isPreset(state.seconds)) {
                    Text(stringResource(R.string.disappearing_custom_note), style = MaterialTheme.typography.bodySmall)
                }
                if (state.notAllowed) {
                    Text(stringResource(R.string.disappearing_not_allowed), color = MaterialTheme.colorScheme.error)
                }
                if (state.failed) {
                    Text(stringResource(R.string.disappearing_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.disappearing_done)) } },
    )
}
