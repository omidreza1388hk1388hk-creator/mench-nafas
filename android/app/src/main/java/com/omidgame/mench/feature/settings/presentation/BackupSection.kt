package com.omidgame.mench.feature.settings.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.backup.BackupManager
import com.omidgame.mench.core.backup.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val backup: BackupManager,
) : ViewModel() {
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message

    fun create(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _message.value = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { backup.write(passphrase, it) } }
            }.fold({ R.string.backup_created }, { R.string.backup_failed })
        }
    }

    fun restore(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _message.value = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { backup.restore(passphrase, it) } }
            }.fold(
                {
                    when (it) {
                        RestoreResult.OK -> R.string.backup_restored
                        RestoreResult.NOT_A_BACKUP -> R.string.backup_not_a_backup
                        RestoreResult.WRONG_PASSPHRASE_OR_CORRUPT -> R.string.backup_wrong_passphrase
                    }
                },
                { R.string.backup_failed },
            )
        }
    }
}

/** Encrypted backup & restore (settings + secure-chat key + readable message archive), protected by a passphrase. */
@Composable
fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf<String?>(null) } // "create" | "restore"
    var passphrase by remember { mutableStateOf("") }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingUri = uri; mode = "create"; passphrase = "" }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; mode = "restore"; passphrase = "" }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.backup_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { createLauncher.launch("mench-backup.menchbak") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_create)) }
        OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_restore)) }
        message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    }

    if (mode != null && pendingUri != null) {
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = { mode = null; pendingUri = null },
            title = { Text(stringResource(if (mode == "create") R.string.backup_create else R.string.backup_restore)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (mode == "create") R.string.backup_passphrase_new else R.string.backup_passphrase_enter), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = passphrase, onValueChange = { passphrase = it }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphrase.length >= (if (mode == "create") 8 else 1),
                    onClick = {
                        val uri = pendingUri!!
                        if (mode == "create") viewModel.create(uri, passphrase) else viewModel.restore(uri, passphrase)
                        mode = null; pendingUri = null; passphrase = ""
                    },
                ) { Text(stringResource(R.string.backup_confirm)) }
            },
            dismissButton = { TextButton(onClick = { mode = null; pendingUri = null }) { Text(stringResource(R.string.us_cancel)) } },
        )
    }
}
