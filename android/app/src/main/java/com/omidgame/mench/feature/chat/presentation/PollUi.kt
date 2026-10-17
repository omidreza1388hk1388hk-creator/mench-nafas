package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.feature.chat.domain.Poll
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Everything a poll bubble needs; provided once by ChatScreen through [LocalPollUi] (same trick as VoiceUi). */
@Stable
class PollUi(
    val polls: StateFlow<Map<String, Poll>>,
    val selfUserId: StateFlow<String?>,
    /** String resources of transient failures ("couldn't vote", "couldn't create the poll"). */
    val errors: SharedFlow<Int>,
    val onLoad: (pollId: String) -> Unit,
    val onVote: (poll: Poll, optionIds: List<String>) -> Unit,
    val onClose: (poll: Poll) -> Unit,
)

val LocalPollUi = staticCompositionLocalOf<PollUi?> { null }

/** The poll inside a message bubble: question, tappable options with live percentages, voter count, close button for the creator. */
@Composable
fun PollBubbleContent(pollId: String) {
    val ui = LocalPollUi.current
    if (ui == null) {
        Text(stringResource(R.string.preview_poll))
        return
    }
    val poll by remember(ui, pollId) { ui.polls.map { it[pollId] }.distinctUntilChanged() }.collectAsState(initial = ui.polls.value[pollId])
    val selfId by ui.selfUserId.collectAsState()
    LaunchedEffect(pollId) { ui.onLoad(pollId) }

    val current = poll
    if (current == null) {
        Text(stringResource(R.string.poll_loading), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val mine = current.myChoices(selfId)
    val totalVotes = current.options.sumOf { it.voterIds.size }
    Column(modifier = Modifier.widthIn(min = 220.dp, max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(current.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            text = stringResource(
                when {
                    current.closed -> R.string.poll_closed
                    current.multiple -> R.string.poll_multiple_hint
                    else -> R.string.poll_single_hint
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.75f),
        )
        current.options.forEach { option ->
            val chosen = option.id in mine
            val fraction = if (totalVotes == 0) 0f else option.voterIds.size.toFloat() / totalVotes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !current.closed) { ui.onVote(current, current.choicesAfterTap(option.id, selfId)) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = when {
                            current.multiple && chosen -> Icons.Filled.CheckBox
                            current.multiple -> Icons.Filled.CheckBoxOutlineBlank
                            chosen -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.RadioButtonUnchecked
                        },
                        contentDescription = stringResource(if (chosen) R.string.poll_option_selected else R.string.poll_option_not_selected),
                    )
                    Text(option.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    color = LocalContentColor.current,
                    trackColor = LocalContentColor.current.copy(alpha = 0.2f),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.poll_voters, current.totalVoters, current.totalVoters),
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
            )
            if (!current.closed && current.creatorId == selfId) {
                TextButton(onClick = { ui.onClose(current) }) { Text(stringResource(R.string.poll_close)) }
            }
        }
    }
}

/** New-poll form: question, 2–10 options, multiple-answers switch. */
@Composable
fun PollCreateDialog(onCreate: (question: String, options: List<String>, multiple: Boolean) -> Unit, onDismiss: () -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multiple by remember { mutableStateOf(false) }
    val cleaned = options.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    val valid = question.isNotBlank() && cleaned.size >= 2

    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.poll_new)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(200) },
                    label = { Text(stringResource(R.string.poll_question_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { index, text ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { options[index] = it.take(100) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.poll_option_hint, index + 1)) },
                            modifier = Modifier.weight(1f),
                        )
                        if (options.size > 2) {
                            IconButton(onClick = { options.removeAt(index) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.poll_remove_option))
                            }
                        }
                    }
                }
                if (options.size < 10) {
                    TextButton(onClick = { options.add("") }) { Text(stringResource(R.string.poll_add_option)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.poll_allow_multiple), modifier = Modifier.weight(1f))
                    Switch(checked = multiple, onCheckedChange = { multiple = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(question.trim(), cleaned, multiple) }, enabled = valid) {
                Text(stringResource(R.string.chat_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
