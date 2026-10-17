package com.omidgame.mench.core.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.a11y.minTouchTarget
import com.omidgame.mench.core.ui.components.MenchDialog


/** Pure helper (JVM-testable): does what the user typed match the required word? Case/space tolerant. */
object TypedConfirm {
    fun matches(typed: String, required: String): Boolean =
        typed.trim().equals(required.trim(), ignoreCase = true) && required.isNotBlank()
}

/**
 * Confirmation dialog. With [requiredWord] set the confirm button stays disabled until the user types
 * it (use for destructive actions: delete account, reset all). Dismissing never confirms.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    requiredWord: String? = null,
    destructive: Boolean = false,
) {
    var typed by remember { mutableStateOf("") }
    val canConfirm = requiredWord == null || TypedConfirm.matches(typed, requiredWord)
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (requiredWord != null) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.sx_confirm_type_hint, requiredWord)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm, modifier = Modifier.minTouchTarget()) {
                Text(
                    confirmLabel,
                    color = if (destructive && canConfirm) MaterialTheme.colorScheme.error else Color.Unspecified,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
