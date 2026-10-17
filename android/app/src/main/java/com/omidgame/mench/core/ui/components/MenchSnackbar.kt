package com.omidgame.mench.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.MenchElevation
import com.omidgame.mench.core.ui.theme.MenchRadius
import com.omidgame.mench.core.ui.theme.MenchSpacing

/**
 * Branded snackbar host. Use it as `snackbarHost = { MenchSnackbarHost(hostState) }` in a
 * Scaffold (or place it in a Box). Replaces the default Material look everywhere.
 */
@Composable
fun MenchSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data -> MenchSnackbar(data) }
}

@Composable
fun MenchSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .padding(horizontal = MenchSpacing.lg, vertical = MenchSpacing.sm)
            .fillMaxWidth(),
        shape = RoundedCornerShape(MenchRadius.lg),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = MenchElevation.high,
        border = BorderStroke(1.dp, MenchBrand.hairline()),
    ) {
        Row(
            modifier = Modifier.padding(start = MenchSpacing.lg, end = MenchSpacing.sm, top = MenchSpacing.xs, bottom = MenchSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MenchSpacing.sm),
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = MenchSpacing.md),
            )
            val actionLabel = data.visuals.actionLabel
            if (actionLabel != null) {
                TextButton(onClick = { data.performAction() }) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}

/** Branded "toast": a short snackbar with no action. */
suspend fun SnackbarHostState.menchToast(message: String) {
    showSnackbar(message = message, duration = SnackbarDuration.Short)
}
