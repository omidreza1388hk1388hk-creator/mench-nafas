package com.omidgame.mench.feature.calls.presentation

import com.omidgame.mench.core.ui.theme.MenchCalendar
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.omidgame.mench.core.ui.components.MenchDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.feature.calls.domain.CallFilter
import com.omidgame.mench.feature.calls.domain.CallHistoryEntry
import com.omidgame.mench.feature.calls.domain.CallHistoryLogic
import com.omidgame.mench.feature.calls.domain.CallRepository
import com.omidgame.mench.feature.calls.domain.CallStatus
import com.omidgame.mench.feature.calls.domain.CallType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Date
import javax.inject.Inject

/** Loads the call log once when the dialog opens. */
@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val callRepository: CallRepository,
) : ViewModel() {
    sealed interface State {
        data object Loading : State
        data object Failed : State
        data class Loaded(val entries: List<CallHistoryEntry>) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    init {
        viewModelScope.launch {
            _state.value = runCatching { callRepository.listHistory() }
                .fold({ State.Loaded(it) }, { State.Failed })
        }
    }
}

/** Counts missed incoming calls since the log was last opened (drives the badge on the calls icon). Reloads whenever [refreshKey] changes. */
@HiltViewModel
class MissedCallsViewModel @Inject constructor(
    private val callRepository: CallRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {
    private val _missed = MutableStateFlow(0)
    val missed: StateFlow<Int> = _missed

    fun refresh() {
        viewModelScope.launch {
            val entries = runCatching { callRepository.listHistory(limit = 50) }.getOrNull() ?: return@launch
            _missed.value = CallHistoryLogic.missedSince(entries, CallPrefs.lastSeenMillis(appContext))
        }
    }
}

/** Call log: direction arrow, who, voice/video, when (Jalali) and how long. Missed calls are red. Filter chips; tap a row to call back. */
@Composable
fun CallHistoryDialog(onDismiss: () -> Unit, onCallBack: (conversationId: String, callType: CallType) -> Unit = { _, _ -> }) {
    val viewModel: CallHistoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { CallPrefs.markSeenNow(context) } }
    var filter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CallFilter.ALL) }
    val format = SimpleDateFormat("d MMMM  HH:mm", MenchCalendar.locale())
    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        title = { Text("📞  " + stringResource(R.string.calls_title)) },
        text = {
            when (val current = state) {
                CallHistoryViewModel.State.Loading -> Text(stringResource(R.string.question_loading))
                CallHistoryViewModel.State.Failed -> Text(stringResource(R.string.calls_failed))
                is CallHistoryViewModel.State.Loaded ->
                    if (current.entries.isEmpty()) {
                        Text(stringResource(R.string.calls_empty))
                    } else {
                        val shown = CallHistoryLogic.filter(current.entries, filter)
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                CallFilter.values().forEach { option ->
                                    androidx.compose.material3.FilterChip(
                                        selected = filter == option,
                                        onClick = { filter = option },
                                        label = {
                                            Text(
                                                stringResource(
                                                    when (option) {
                                                        CallFilter.ALL -> R.string.filter_all
                                                        CallFilter.MISSED -> R.string.calls_filter_missed
                                                        CallFilter.VOICE -> R.string.calls_filter_voice
                                                        CallFilter.VIDEO -> R.string.calls_filter_video
                                                    },
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                            if (shown.isEmpty()) Text(stringResource(R.string.calls_empty_filtered))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(shown, key = { it.id }) { entry ->
                                val missed = CallHistoryLogic.isHighlightedAsMissed(entry)
                                val arrow = when {
                                    missed -> "↙"
                                    entry.wasOutgoing -> "↗"
                                    else -> "↙"
                                }
                                val whenText = runCatching {
                                    format.format(Date(Instant.parse(entry.startedAtIso).toEpochMilli()))
                                }.getOrDefault(entry.startedAtIso)
                                val duration = entry.durationSeconds?.takeIf { it > 0 }
                                    ?.let { String.format("%d:%02d", it / 60, it % 60) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .androidx_clickable(stringResource(R.string.calls_call_back)) {
                                            onCallBack(entry.conversationId, entry.callType)
                                            onDismiss()
                                        },
                                ) {
                                    Row {
                                        Text(
                                            text = arrow + " " + (entry.otherDisplayName ?: entry.otherPhoneE164),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(if (entry.callType == CallType.VIDEO) "🎥" else "📞")
                                    }
                                    Text(
                                        text = listOfNotNull(whenText, duration).joinToString("  •  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                        }
                    }
            }
        },
    )
}

private fun Modifier.androidx_clickable(label: String, onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClickLabel = label, onClick = onClick))
