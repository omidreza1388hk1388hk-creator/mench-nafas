package com.omidgame.mench.core.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchSheet

/** One selectable option. [subtitle] is optional helper text shown under the label. */
data class ChoiceOption<T>(val value: T, val label: String, val subtitle: String? = null)

/**
 * A row that shows the current choice as its trailing value and opens a bottom sheet with a
 * single-choice list. Picking an option applies it immediately and closes the sheet.
 */
@Composable
fun <T> SingleChoiceRow(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }?.label.orEmpty()
    SettingsRow(
        title = title,
        subtitle = subtitle,
        highlighted = highlighted,
        enabled = enabled,
        modifier = modifier,
        onClick = { open = true },
        trailing = {
            Text(current, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        },
    )
    if (open) {
        MenchSheet(onDismissRequest = { open = false }) {
            SheetTitle(title)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = option.value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelected(option.value)
                                    open = false
                                },
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.value == selected, onClick = null)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            if (option.subtitle != null) {
                                Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Multi-select version: edits a working copy and applies it when the user taps Done, so a
 * half-finished selection is never stored. [summary] is shown as the trailing value.
 */
@Composable
fun <T> MultiChoiceRow(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: Set<T>,
    onSelectionChange: (Set<T>) -> Unit,
    summary: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    SettingsRow(
        title = title,
        subtitle = subtitle,
        highlighted = highlighted,
        enabled = enabled,
        modifier = modifier,
        onClick = { open = true },
        trailing = {
            Text(summary, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        },
    )
    if (open) {
        var working by remember { mutableStateOf(selected) }
        MenchSheet(onDismissRequest = { open = false }) {
            SheetTitle(title)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    val checked = option.value in working
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { on -> working = if (on) working + option.value else working - option.value },
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(14.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
            Button(
                onClick = {
                    onSelectionChange(working)
                    open = false
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.sx_done)) }
        }
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}
