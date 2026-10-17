package com.omidgame.mench.feature.product

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.BuildConfig
import com.omidgame.mench.R
import com.omidgame.mench.core.diagnostics.CrashLogger
import com.omidgame.mench.core.ui.components.menchToast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------ Help / FAQ

@Composable
fun HelpScreen(onBack: () -> Unit, onFeedback: () -> Unit) {
    val questions = stringArrayResource(R.array.faq_questions)
    val answers = stringArrayResource(R.array.faq_answers)
    ProductScaffold(title = stringResource(R.string.help_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            questions.forEachIndexed { i, q ->
                var open by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().clickable { open = !open }.animateContentSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(q, style = MaterialTheme.typography.titleSmall)
                        if (open) Text(answers.getOrElse(i) { "" }, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(onClick = onFeedback, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.feedback_title)) }
            val appContext = LocalContext.current.applicationContext
            AnalyticsConsentRow(prefs = remember { ProductPrefs(appContext) })
        }
    }
}

// ------------------------------------------------------------------ Feedback

@HiltViewModel
class FeedbackViewModel @Inject constructor(private val repo: ProductRepository) : ViewModel() {
    fun send(context: Context, category: String, message: String, attachLogs: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val logs = if (attachLogs) runCatching { CrashLogger.readLatest(context) }.getOrNull() else null
            val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            repo.sendFeedback(category, message, BuildConfig.VERSION_NAME, device, logs).onSuccess { onResult(true) }.onFailure { onResult(false) }
        }
    }
}

@Composable
fun FeedbackScreen(onBack: () -> Unit, viewModel: FeedbackViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var category by remember { mutableStateOf("general") }
    var message by remember { mutableStateOf("") }
    var attachLogs by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val sentText = stringResource(R.string.feedback_sent)
    val failText = stringResource(R.string.product_error_generic)
    val categories = listOf("general" to R.string.feedback_cat_general, "bug" to R.string.feedback_cat_bug, "idea" to R.string.feedback_cat_idea)

    ProductScaffold(title = stringResource(R.string.feedback_title), onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { (key, res) -> FilterChip(selected = category == key, onClick = { category = key }, label = { Text(stringResource(res)) }) }
            }
            OutlinedTextField(
                value = message, onValueChange = { message = it.take(4000) },
                label = { Text(stringResource(R.string.feedback_message_hint)) },
                modifier = Modifier.fillMaxWidth(), minLines = 5,
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = attachLogs, onCheckedChange = { attachLogs = it })
                Text(stringResource(R.string.feedback_attach_logs), modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.feedback_logs_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    sending = true
                    viewModel.send(context, category, message.trim(), attachLogs) { ok ->
                        sending = false
                        scope.launch { snackbar.menchToast(if (ok) sentText else failText) }
                        if (ok) { message = "" }
                    }
                },
                enabled = message.trim().length >= 3 && !sending,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feedback_send)) }
        }
    }
}

// ------------------------------------------------------------------ Legal / About / Changelog

@Composable
fun LegalScreen(onBack: () -> Unit) {
    ProductScaffold(title = stringResource(R.string.legal_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.legal_terms_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.legal_terms_body), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.legal_privacy_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.legal_privacy_body), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.legal_review_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val versions = stringArrayResource(R.array.changelog_versions)
    val notes = stringArrayResource(R.array.changelog_notes)
    ProductScaffold(title = stringResource(R.string.changelog_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            versions.forEachIndexed { i, v ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(v, style = MaterialTheme.typography.titleSmall)
                        Text(notes.getOrElse(i) { "" }, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Analytics consent (shown on Help-adjacent settings)

@Composable
fun AnalyticsConsentRow(prefs: ProductPrefs, modifier: Modifier = Modifier) {
    var enabled by remember { mutableStateOf(prefs.analyticsEnabled) }
    Row(modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.analytics_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = { enabled = it; prefs.analyticsEnabled = it })
    }
}
