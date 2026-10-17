package com.omidgame.mench.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.network.ChecklistItem
import com.omidgame.mench.core.network.PlannerBody
import com.omidgame.mench.core.network.PlannerItem
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.core.ui.theme.MenchDigits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import javax.inject.Inject

data class PlannerUiState(val filter: String = "today", val loading: Boolean = true, val items: List<PlannerItem> = emptyList(), val error: Boolean = false)

@HiltViewModel
class PlannerViewModel @Inject constructor(private val repo: EcosystemRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state

    init { load("today") }

    fun load(filter: String = _state.value.filter) {
        _state.update { it.copy(filter = filter, loading = true, error = false) }
        viewModelScope.launch {
            repo.planner(filter)
                .onSuccess { list -> _state.update { it.copy(loading = false, items = list) } }
                .onFailure { _state.update { s -> s.copy(loading = false, error = true) } }
        }
    }

    fun create(body: PlannerBody) { viewModelScope.launch { repo.createPlanner(body).onSuccess { load() } } }
    fun toggle(item: PlannerItem) { viewModelScope.launch { repo.complete(item.id, item.completedAt == null).onSuccess { load() } } }
    /** Toggles one checklist line and saves the whole list (the server replaces the checklist as a unit). */
    fun toggleChecklist(item: PlannerItem, index: Int) {
        val updated = item.checklist.mapIndexed { i, c -> if (i == index) c.copy(done = !c.done) else c }
        viewModelScope.launch { repo.patchPlanner(item.id, PlannerBody(title = item.title, checklist = updated)).onSuccess { load() } }
    }
    fun delete(item: PlannerItem) { viewModelScope.launch { repo.deletePlanner(item.id).onSuccess { load() } } }
}

private val FILTERS = listOf("overdue" to R.string.planner_overdue, "today" to R.string.planner_today, "upcoming" to R.string.planner_upcoming, "completed" to R.string.planner_completed)

@Composable
fun PlannerScreen(onBack: () -> Unit, viewModel: PlannerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    ProductScaffold(title = stringResource(R.string.planner_title), onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FILTERS.forEach { (key, res) ->
                        FilterChip(selected = state.filter == key, onClick = { viewModel.load(key) }, label = { Text(stringResource(res)) })
                    }
                }
                when {
                    state.error -> Text(stringResource(R.string.product_error_generic), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                    !state.loading && state.items.isEmpty() -> Text(stringResource(R.string.planner_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                    else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 4.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.items, key = { it.id }) { item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = item.completedAt != null, onCheckedChange = { viewModel.toggle(item) })
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                                    formatDue(item.dueAt)?.let { Text(MenchDigits.toPersian(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    item.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    if (item.participants.isNotEmpty()) {
                                        Text(stringResource(R.string.event_card_with, item.participants.joinToString("، ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    item.checklist.forEachIndexed { index, line ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = line.done, onCheckedChange = { viewModel.toggleChecklist(item, index) })
                                            Text(line.text, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                IconButton(onClick = { viewModel.delete(item) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.message_delete_failed)) }
                            }
                        }
                    }
                }
            }
            FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.planner_add))
            }
        }
    }
    if (showAdd) AddPlannerDialog(onDismiss = { showAdd = false }, onCreate = { viewModel.create(it); showAdd = false })
}

private fun formatDue(iso: String?): String? = iso?.let {
    runCatching { ZonedDateTime.ofInstant(Instant.parse(it), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) }.getOrNull()
}

/** Create dialog shared by the Reminders screen and the "Remind me" message action. */
@Composable
fun AddPlannerDialog(
    onDismiss: () -> Unit,
    onCreate: (PlannerBody) -> Unit,
    initialTitle: String = "",
    conversationId: String? = null,
    messageId: String? = null,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("reminder") }
    var repeat by remember { mutableStateOf("none") }
    var due by remember { mutableStateOf<Calendar?>(null) }
    var location by remember { mutableStateOf("") }
    var participants by remember { mutableStateOf("") }
    var checklist by remember { mutableStateOf(listOf<String>()) }
    var newLine by remember { mutableStateOf("") }

    fun preset(minutes: Int) { due = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) } }
    fun tomorrowMorning() { due = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) } }
    var showPicker by remember { mutableStateOf(false) }
    fun custom() { showPicker = true }
    if (showPicker) {
        // Jalali picker (Phase 1D.4 leftover); the result is still a Calendar so the rest of the dialog is unchanged.
        com.omidgame.mench.core.ui.components.JalaliDateTimePickerDialog(
            initialMillis = due?.timeInMillis,
            onDismiss = { showPicker = false },
            onPicked = { millis -> due = Calendar.getInstance().apply { timeInMillis = millis }; showPicker = false },
        )
    }

    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planner_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, label = { Text(stringResource(R.string.planner_title_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it.take(2000) }, label = { Text(stringResource(R.string.planner_note_hint)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("reminder" to R.string.planner_kind_reminder, "task" to R.string.planner_kind_task, "event" to R.string.planner_kind_event).forEach { (k, r) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(r)) })
                    }
                }
                if (kind == "event") {
                    OutlinedTextField(value = location, onValueChange = { location = it.take(200) }, label = { Text(stringResource(R.string.planner_location_hint)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = participants, onValueChange = { participants = it.take(300) }, label = { Text(stringResource(R.string.planner_participants_hint)) }, modifier = Modifier.fillMaxWidth())
                }
                if (kind == "task") {
                    checklist.forEachIndexed { i, line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(line, modifier = Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = { checklist = checklist.filterIndexed { j, _ -> j != i } }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.planner_checklist_remove))
                            }
                        }
                    }
                    if (checklist.size < 50) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newLine, onValueChange = { newLine = it.take(200) }, label = { Text(stringResource(R.string.planner_checklist_hint)) }, modifier = Modifier.weight(1f), singleLine = true)
                            TextButton(enabled = newLine.isNotBlank(), onClick = { checklist = checklist + newLine.trim(); newLine = "" }) { Text(stringResource(R.string.planner_checklist_add)) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { preset(60) }) { Text(stringResource(R.string.planner_in_1h)) }
                    TextButton(onClick = { preset(180) }) { Text(stringResource(R.string.planner_in_3h)) }
                    TextButton(onClick = { tomorrowMorning() }) { Text(stringResource(R.string.planner_tomorrow)) }
                }
                OutlinedButton(onClick = { custom() }, modifier = Modifier.fillMaxWidth()) {
                    Text(due?.let { MenchDigits.toPersian(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").format(ZonedDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault()))) } ?: stringResource(R.string.planner_pick_date))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("none" to R.string.planner_repeat_none, "daily" to R.string.planner_repeat_daily, "weekly" to R.string.planner_repeat_weekly, "monthly" to R.string.planner_repeat_monthly).forEach { (k, r) ->
                        FilterChip(selected = repeat == k, onClick = { repeat = k }, label = { Text(stringResource(r)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && (repeat == "none" && kind != "event" || due != null),
                onClick = {
                    onCreate(PlannerBody(kind = kind, title = title.trim(), note = note.trim().ifBlank { null }, dueAt = due?.toInstant()?.toString(), repeat = repeat, location = location.trim().ifBlank { null }.takeIf { kind == "event" }, conversationId = conversationId, messageId = messageId,
                        checklist = (checklist + listOfNotNull(newLine.trim().ifBlank { null })).map { ChecklistItem(it, false) }.takeIf { kind == "task" && it.isNotEmpty() },
                        participants = participants.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }.takeIf { kind == "event" && it.isNotEmpty() }))
                },
            ) { Text(stringResource(R.string.feedback_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
