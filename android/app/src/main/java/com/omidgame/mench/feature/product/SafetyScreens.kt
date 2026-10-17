package com.omidgame.mench.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row as LayoutRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.network.RestrictionResponse
import com.omidgame.mench.core.network.TransparencyResponse
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.feature.chat.domain.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val CATEGORIES = listOf(
    "spam" to R.string.report_cat_spam, "harassment" to R.string.report_cat_harassment, "violence" to R.string.report_cat_violence,
    "sexual" to R.string.report_cat_sexual, "scam" to R.string.report_cat_scam, "impersonation" to R.string.report_cat_impersonation,
    "other" to R.string.report_cat_other,
)

// ---------------------------------------------------------------- message actions (Save / Remind / Report)

@HiltViewModel
class MessageProductActionsViewModel @Inject constructor(
    private val repo: EcosystemRepository,
    private val chatRepository: com.omidgame.mench.feature.chat.domain.ChatRepository,
) : ViewModel() {
    fun save(messageId: String) { viewModelScope.launch { repo.saveMessage(messageId) } }
    fun report(messageId: String, category: String, details: String?) { viewModelScope.launch { repo.report(messageId, null, category, details) } }
    fun remind(body: com.omidgame.mench.core.network.PlannerBody) {
        viewModelScope.launch {
            repo.createPlanner(body)
            // An event created from a chat is also posted into that chat as a MENCH event card.
            if (body.kind == "event" && body.conversationId != null) {
                val e = SharedEvent(body.title, body.dueAt, body.location, body.note, body.participants ?: emptyList())
                chatRepository.sendMessage(body.conversationId, EventCodec.encode(e, EventCodec.fallback(e)))
            }
        }
    }
}

/**
 * Extra rows for the long-press message sheet: save to cloud, remind me, report. The rows do NOT
 * dismiss the sheet themselves: the dialogs live inside this composable, so the sheet must stay
 * composed until the dialog finishes; [onDone] then closes the sheet.
 */
@Composable
fun ColumnScope.ProductMessageActions(message: Message, onDone: () -> Unit) {
    val serverId = message.serverId ?: return
    val vm: MessageProductActionsViewModel = hiltViewModel()
    var showRemind by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

    @Composable
    fun Row(label: String, destructive: Boolean = false, onClick: () -> Unit) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }

    Row(stringResource(R.string.action_save_to_cloud)) { vm.save(serverId); onDone() }
    Row(stringResource(R.string.action_remind_me)) { showRemind = true }
    if (!message.isOwn) Row(stringResource(R.string.action_report), destructive = true) { showReport = true }

    if (showRemind) {
        AddPlannerDialog(
            onDismiss = { showRemind = false; onDone() },
            onCreate = { vm.remind(it); showRemind = false; onDone() },
            initialTitle = (message.body ?: "").take(120),
            conversationId = message.conversationId,
            messageId = serverId,
        )
    }
    if (showReport) {
        ReportDialog(
            onDismiss = { showReport = false; onDone() },
            onSubmit = { cat, details -> vm.report(serverId, cat, details); showReport = false; onDone() },
        )
    }
}

@Composable
fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String, String?) -> Unit) {
    var category by remember { mutableStateOf("spam") }
    var details by remember { mutableStateOf("") }
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_report)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.report_explainer), style = MaterialTheme.typography.bodySmall)
                CATEGORIES.forEach { (key, res) ->
                    LayoutRow(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = category == key, onClick = { category = key })
                        Text(stringResource(res))
                    }
                }
                OutlinedTextField(value = details, onValueChange = { details = it.take(1000) }, label = { Text(stringResource(R.string.report_details_hint)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(category, details.trim().ifBlank { null }) }) { Text(stringResource(R.string.feedback_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ---------------------------------------------------------------- restriction notice + appeal

data class RestrictionUiState(val restriction: RestrictionResponse? = null, val appealSent: Boolean = false)

@HiltViewModel
class RestrictionViewModel @Inject constructor(private val repo: EcosystemRepository) : ViewModel() {
    private val _state = MutableStateFlow(RestrictionUiState())
    val state: StateFlow<RestrictionUiState> = _state

    init { load() }

    fun load() { viewModelScope.launch { repo.restriction().onSuccess { r -> _state.update { it.copy(restriction = r.takeIf { x -> x.restricted }) } } } }

    fun appeal(message: String) {
        val id = _state.value.restriction?.id ?: return
        viewModelScope.launch { repo.appeal(id, message).onSuccess { _state.update { it.copy(appealSent = true) } } }
    }
}

/** Shown at the top of Settings only while the account is restricted; explains why and offers an appeal. */
@Composable
fun RestrictionNotice(modifier: Modifier = Modifier, viewModel: RestrictionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val r = state.restriction ?: return
    var showAppeal by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Card(modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.restriction_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(r.reason ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            r.expiresAt?.let { Text(stringResource(R.string.restriction_until, it.take(16).replace('T', ' ')), style = MaterialTheme.typography.bodySmall) }
            if (r.automatic) Text(stringResource(R.string.restriction_automatic), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                state.appealSent || r.appealStatus == "open" -> Text(stringResource(R.string.restriction_appeal_sent), modifier = Modifier.padding(top = 8.dp))
                else -> TextButton(onClick = { showAppeal = true }) { Text(stringResource(R.string.restriction_appeal)) }
            }
        }
    }
    if (showAppeal) {
        MenchDialog(
            onDismissRequest = { showAppeal = false },
            title = { Text(stringResource(R.string.restriction_appeal)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, label = { Text(stringResource(R.string.restriction_appeal_hint)) }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = text.trim().length >= 5, onClick = { viewModel.appeal(text.trim()); showAppeal = false }) { Text(stringResource(R.string.feedback_send)) } },
            dismissButton = { TextButton(onClick = { showAppeal = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- transparency

@HiltViewModel
class TransparencyViewModel @Inject constructor(private val repo: EcosystemRepository) : ViewModel() {
    private val _data = MutableStateFlow<TransparencyResponse?>(null)
    val data: StateFlow<TransparencyResponse?> = _data
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error

    init { viewModelScope.launch { repo.transparency().onSuccess { _data.value = it }.onFailure { _error.value = true } } }
}

@Composable
fun TransparencyScreen(onBack: () -> Unit, viewModel: TransparencyViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    ProductScaffold(title = stringResource(R.string.transparency_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val d = data
            if (d == null) {
                Text(stringResource(if (error) R.string.product_error_generic else R.string.referral_loading))
            } else {
                Text(stringResource(R.string.transparency_period, d.periodDays), style = MaterialTheme.typography.labelLarge)
                Text(d.howToReport, style = MaterialTheme.typography.bodyMedium)
                Text(d.policies, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.transparency_reports), style = MaterialTheme.typography.titleSmall)
                d.reportsByCategory.forEach { Text("${it.category}: ${it.n}") }
                Text(stringResource(R.string.transparency_outcomes), style = MaterialTheme.typography.titleSmall)
                d.reportsByStatus.forEach { Text("${it.status}: ${it.n}") }
                Text(stringResource(R.string.transparency_actions), style = MaterialTheme.typography.titleSmall)
                d.restrictions.forEach { Text("${it.kind} (${stringResource(if (it.automatic == true) R.string.transparency_auto else R.string.transparency_manual)}): ${it.n}") }
                Text(stringResource(R.string.transparency_appeals), style = MaterialTheme.typography.titleSmall)
                d.appeals.forEach { Text("${it.status}: ${it.n}") }
            }
        }
    }
}
