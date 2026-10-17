package com.omidgame.mench.feature.settings.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omidgame.mench.R
import com.omidgame.mench.core.backup.PassphraseCheck
import com.omidgame.mench.core.backup.PassphraseRules
import com.omidgame.mench.core.ui.settings.BannerKind
import com.omidgame.mench.core.ui.settings.InfoBanner
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.core.ui.settings.SwitchRow

/** Export my data: user picks where the file goes; optional passphrase encryption; clear warning when unencrypted. */
@Composable
fun DataExportScreen(onBack: () -> Unit, viewModel: DataExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var protect by remember { mutableStateOf(true) } // safe default: encrypted
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val check = PassphraseRules.check(pass, confirm)
    val canSave = state.phase != ExportPhase.WORKING && (!protect || check == PassphraseCheck.OK)

    val saveEncrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.export(uri, pass)
    }
    val savePlain = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri, null)
    }

    SettingsScaffold(title = stringResource(R.string.sx_exp_title), onBack = onBack) {
        Text(stringResource(R.string.sx_exp_explainer), style = MaterialTheme.typography.bodyMedium)

        if (!protect) {
            InfoBanner(text = stringResource(R.string.sx_exp_unencrypted_warning), kind = BannerKind.WARNING)
        }

        SettingsSection(title = null) {
            SwitchRow(
                title = stringResource(R.string.sx_exp_protect),
                subtitle = stringResource(R.string.sx_exp_protect_sub),
                checked = protect,
                onCheckedChange = { protect = it },
            )
        }

        if (protect) {
            Column {
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.sx_exp_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = pass.isNotEmpty() && check == PassphraseCheck.TOO_SHORT,
                    supportingText = {
                        if (pass.isNotEmpty() && check == PassphraseCheck.TOO_SHORT) {
                            Text(stringResource(R.string.sx_exp_pass_short, PassphraseRules.MIN_LENGTH))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.sx_exp_passphrase_confirm)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = confirm.isNotEmpty() && check == PassphraseCheck.MISMATCH,
                    supportingText = {
                        if (confirm.isNotEmpty() && check == PassphraseCheck.MISMATCH) {
                            Text(stringResource(R.string.sx_exp_pass_mismatch))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.sx_exp_pass_forgot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Button(
            onClick = {
                viewModel.reset()
                if (protect) saveEncrypted.launch("mench-data-export.menchexp") else savePlain.launch("mench-data-export.json")
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.sx_exp_save)) }

        when (state.phase) {
            ExportPhase.WORKING -> {
                val p = state.progress
                if (p == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.sx_exp_working), style = MaterialTheme.typography.bodySmall)
            }
            ExportPhase.DONE -> InfoBanner(
                text = stringResource(if (state.encrypted) R.string.sx_exp_done_encrypted else R.string.sx_exp_done_plain),
                kind = if (state.encrypted) BannerKind.INFO else BannerKind.WARNING,
            )
            ExportPhase.FAILED -> InfoBanner(text = stringResource(R.string.sx_exp_failed), kind = BannerKind.DANGER)
            ExportPhase.IDLE -> Unit
        }
    }
}
