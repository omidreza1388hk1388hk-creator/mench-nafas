package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.feature.chat.domain.Counter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Phase 3A — shared counters: "days together", "days until the trip", "times we hugged"… */

/** Whole days from [anchorIso] to [today]: positive = days since, negative = days still to go, null = bad date. */
internal fun dateCounterDays(anchorIso: String?, today: LocalDate): Long? =
    runCatching { ChronoUnit.DAYS.between(LocalDate.parse(anchorIso), today) }.getOrNull()

/** The date as the Persian (Jalali) calendar writes it, e.g. «۱۲ مرداد ۱۴۰۳»; null for a missing or unreadable date. Uses the platform's ICU. */
internal fun persianDateText(iso: String?): String? = runCatching {
    val date = LocalDate.parse(iso)
    val format = android.icu.text.SimpleDateFormat("d MMMM yyyy", android.icu.util.ULocale("fa_IR@calendar=persian"))
    format.format(java.util.Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))
}.getOrNull()

/** Emoji suggestions offered when creating a counter. */
private val counterEmojis = listOf("💗", "💍", "✈️", "🎂", "🤗", "🌙")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountersDialog(
    counters: List<Counter>,
    onDismiss: () -> Unit,
    onCreate: (title: String, emoji: String?, kind: String, anchorDateIso: String?, theme: Int) -> Unit,
    onBump: (counterId: String, delta: Int) -> Unit,
    onDelete: (counterId: String) -> Unit,
) {
    var composing by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now() }

    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        dismissButton = { TextButton(onClick = { composing = true }) { Text(stringResource(R.string.counters_add)) } },
        title = { Text("🔢  " + stringResource(R.string.counters_title)) },
        text = {
            if (counters.isEmpty()) {
                EmptyHeartState(stringResource(R.string.counters_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(counters, key = { it.id }) { counter ->
                        val themed = counter.theme in 1..5
                        val onCard = if (themed) Color.White else Color.Unspecified
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (themed) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            androidx.compose.runtime.CompositionLocalProvider(
                                androidx.compose.material3.LocalContentColor provides (if (themed) Color.White else androidx.compose.material3.LocalContentColor.current),
                            ) {
                            Column(
                                modifier = (if (themed) Modifier.background(storyBrush(counter.theme)) else Modifier).padding(12.dp),
                            ) {
                                Text(counter.emoji + "  " + counter.title, style = MaterialTheme.typography.titleSmall)
                                if (counter.kind == "date") persianDateText(counter.anchorDate)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = if (themed) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (counter.kind == "tally") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        OutlinedButton(
                                            enabled = counter.tallyCount > 0,
                                            onClick = { onBump(counter.id, -1) },
                                        ) { Text("−") }
                                        RollingNumber(counter.tallyCount.toLong(), style = MaterialTheme.typography.headlineMedium)
                                        OutlinedButton(onClick = { onBump(counter.id, 1) }) { Text("+") }
                                    }
                                } else {
                                    val days = dateCounterDays(counter.anchorDate, today)
                                    val text = when {
                                        days == null -> ""
                                        days == 0L -> stringResource(R.string.counters_today)
                                        days > 0 -> stringResource(R.string.counters_since, days)
                                        else -> stringResource(R.string.counters_until, -days)
                                    }
                                    Text(text, style = MaterialTheme.typography.headlineSmall, color = if (themed) Color.White else MaterialTheme.colorScheme.primary)
                                    if (days != null && days > 0) {
                                        if (isMilestone(days)) {
                                            SparkleBurst(size = 56.dp, color = if (themed) Color.White else MaterialTheme.colorScheme.tertiary)
                                            Text(stringResource(R.string.counters_milestone, days), style = MaterialTheme.typography.bodyMedium)
                                        } else {
                                            val (target, left) = nextMilestone(days)
                                            Text(
                                                stringResource(R.string.counters_next_milestone, left, target),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                if (counter.isMine) {
                                    TextButton(onClick = { confirmDeleteId = counter.id }) {
                                        Text(stringResource(R.string.counters_delete))
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        },
    )

    confirmDeleteId?.let { id ->
        MenchDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text(stringResource(R.string.counters_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { onDelete(id); confirmDeleteId = null }) { Text(stringResource(R.string.counters_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text(stringResource(R.string.us_cancel)) } },
        )
    }

    if (composing) {
        var title by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf(counterEmojis.first()) }
        var kind by remember { mutableStateOf("date") }
        var dateIso by remember { mutableStateOf<String?>(null) }
        var pickingDate by remember { mutableStateOf(false) }
        var theme by remember { mutableStateOf(0) }
        MenchDialog(
            onDismissRequest = { composing = false },
            title = { Text(stringResource(R.string.counters_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 60) title = it },
                        label = { Text(stringResource(R.string.counters_title_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        counterEmojis.forEach { e ->
                            FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == "date", onClick = { kind = "date" }, label = { Text(stringResource(R.string.counters_kind_date)) })
                        FilterChip(selected = kind == "tally", onClick = { kind = "tally" }, label = { Text(stringResource(R.string.counters_kind_tally)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        (0..5).forEach { t ->
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(if (t == theme) 34.dp else 28.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (t == 0) androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant) else storyBrush(t))
                                    .clickable { theme = t },
                            )
                        }
                    }
                    if (kind == "date") {
                        OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(dateIso ?: stringResource(R.string.counters_pick_date))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank() && (kind == "tally" || dateIso != null),
                    onClick = {
                        onCreate(title.trim(), emoji, kind, if (kind == "date") dateIso else null, theme)
                        composing = false
                    },
                ) { Text(stringResource(R.string.counters_create)) }
            },
            dismissButton = { TextButton(onClick = { composing = false }) { Text(stringResource(R.string.us_cancel)) } },
        )
        if (pickingDate) {
            val state = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            dateIso = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        }
                        pickingDate = false
                    }) { Text(stringResource(R.string.us_confirm)) }
                },
                dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.us_cancel)) } },
            ) { DatePicker(state = state) }
        }
    }
}
