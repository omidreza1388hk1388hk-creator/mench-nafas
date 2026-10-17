package com.omidgame.mench.feature.privacy.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R

private enum class Form { NONE, CHANGE, DISABLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoStepSettingsScreen(
    state: TwoStepSettingsUiState,
    onSave: (password: String, confirm: String, hint: String, current: String?) -> Unit,
    onDisable: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    var form by remember { mutableStateOf(Form.NONE) }
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }

    // A successful change/disable closes the form and wipes every field (passwords never linger in state).
    LaunchedEffect(state.completedCounter) {
        current = ""; password = ""; confirm = ""; hint = ""; form = Form.NONE
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.two_step_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.two_step_explainer), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(if (state.enabled) R.string.two_step_on else R.string.two_step_off),
                style = MaterialTheme.typography.titleMedium,
            )

            state.error?.let { Text(stringResource(errorRes(it)), color = MaterialTheme.colorScheme.error) }

            val showCreate = !state.enabled || form == Form.CHANGE
            if (state.enabled && form == Form.NONE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { form = Form.CHANGE }) { Text(stringResource(R.string.two_step_change)) }
                    OutlinedButton(onClick = { form = Form.DISABLE }) { Text(stringResource(R.string.two_step_disable)) }
                }
            }
            if (state.enabled && form == Form.DISABLE) {
                PasswordField(stringResource(R.string.two_step_current_password), current) { current = it }
                Button(onClick = { onDisable(current) }, enabled = !state.busy && current.isNotEmpty()) {
                    Text(stringResource(R.string.two_step_disable))
                }
            }
            if (showCreate) {
                if (state.enabled) PasswordField(stringResource(R.string.two_step_current_password), current) { current = it }
                PasswordField(stringResource(R.string.two_step_new_password), password) { password = it }
                PasswordField(stringResource(R.string.two_step_confirm_password), confirm) { confirm = it }
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it.take(80) },
                    label = { Text(stringResource(R.string.two_step_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.two_step_forgot_warning), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { onSave(password, confirm, hint, if (state.enabled) current else null) },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.two_step_save)) }
            }
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(64)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun errorRes(error: TwoStepError): Int = when (error) {
    TwoStepError.NETWORK -> R.string.two_step_err_network
    TwoStepError.WRONG_PASSWORD -> R.string.two_step_err_wrong
    TwoStepError.LOCKED -> R.string.two_step_err_locked
    TwoStepError.REJECTED -> R.string.two_step_err_rejected
    TwoStepError.TOO_SHORT -> R.string.two_step_err_short
    TwoStepError.TOO_LONG -> R.string.two_step_err_long
    TwoStepError.REPEATED -> R.string.two_step_err_repeated
    TwoStepError.MISMATCH -> R.string.two_step_err_mismatch
    TwoStepError.HINT -> R.string.two_step_err_hint
}
