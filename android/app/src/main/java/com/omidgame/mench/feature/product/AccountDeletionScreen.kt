package com.omidgame.mench.feature.product

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.notifications.RealtimeServiceLauncher
import com.omidgame.mench.feature.auth.domain.AuthRepository
import com.omidgame.mench.core.security.rememberReauth
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.settings.domain.DeleteAccountFlow
import com.omidgame.mench.feature.settings.domain.DeleteStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class AccountDeletionUiState(
    val exporting: Boolean = false,
    val exportedFile: File? = null,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class AccountDeletionViewModel @Inject constructor(
    private val repo: ProductRepository,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val realtimeServiceLauncher: RealtimeServiceLauncher,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountDeletionUiState())
    val state: StateFlow<AccountDeletionUiState> = _state

    fun export() {
        _state.update { it.copy(exporting = true, error = false, exportedFile = null) }
        viewModelScope.launch {
            repo.exportToFile()
                .onSuccess { f -> _state.update { it.copy(exporting = false, exportedFile = f) } }
                .onFailure { _state.update { s -> s.copy(exporting = false, error = true) } }
        }
    }

    fun exportShared() { _state.update { it.copy(exportedFile = null) } }

    fun delete(confirm: String) {
        _state.update { it.copy(deleting = true, error = false) }
        viewModelScope.launch {
            repo.deleteAccount(confirm)
                .onSuccess {
                    // Same teardown as a normal logout; the server already revoked every session.
                    realtimeServiceLauncher.stop()
                    chatRepository.disconnectRealtime()
                    runCatching { authRepository.logout() }
                    _state.update { it.copy(deleting = false, deleted = true) }
                }
                .onFailure { _state.update { s -> s.copy(deleting = false, error = true) } }
        }
    }
}

@Composable
fun AccountDeletionScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onExportData: () -> Unit = {},
    viewModel: AccountDeletionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(DeleteStep.INFO) }
    var typed by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableStateOf(DeleteAccountFlow.COOLING_SECONDS) }
    var reauthFailed by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    // The wait only counts down while the "think again" step is on screen, and restarts if the user leaves it.
    LaunchedEffect(step) {
        if (step == DeleteStep.COOLING) {
            secondsLeft = DeleteAccountFlow.COOLING_SECONDS
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
            }
        }
    }

    val reauth = rememberReauth(
        title = stringResource(R.string.sx_del_reauth_title),
        cancelText = stringResource(R.string.action_cancel),
        onFailed = { reauthFailed = true },
    )

    ProductScaffold(title = stringResource(R.string.delete_account_title), onBack = {
        // Back walks one step back first; only the first step leaves the screen.
        DeleteAccountFlow.previous(step)?.let { step = it } ?: onBack()
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(
                stringResource(R.string.sx_del_step, step.ordinal + 1, DeleteAccountFlow.stepCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            when (step) {
                DeleteStep.INFO -> {
                    Text(stringResource(R.string.delete_account_explainer), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        stringResource(R.string.delete_account_what_happens),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    OutlinedButton(onClick = onExportData, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                        Text(stringResource(R.string.sx_del_export_link))
                    }
                }
                DeleteStep.CONFIRM -> {
                    Text(stringResource(R.string.sx_del_confirm_msg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text(stringResource(R.string.delete_account_type_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
                DeleteStep.COOLING -> {
                    Text(stringResource(R.string.sx_del_cooling_msg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    if (secondsLeft > 0) {
                        Text(
                            stringResource(R.string.sx_del_cooling_wait, secondsLeft),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                DeleteStep.FINAL -> {
                    Text(stringResource(R.string.sx_del_final_msg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    if (reauthFailed) {
                        Text(stringResource(R.string.sx_del_reauth_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (state.error) {
                        Text(stringResource(R.string.product_error_generic), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                    Button(
                        onClick = {
                            reauthFailed = false
                            reauth { viewModel.delete(DeleteAccountFlow.CONFIRM_WORD) }
                        },
                        enabled = !state.deleting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    ) {
                        if (state.deleting) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.sx_del_final_button))
                    }
                }
            }
            val next = DeleteAccountFlow.next(step)
            if (next != null) {
                Button(
                    onClick = { step = next },
                    enabled = DeleteAccountFlow.canContinue(step, typed, secondsLeft),
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) { Text(stringResource(R.string.sx_del_next)) }
            }
        }
    }
}
