package com.omidgame.mench.feature.security.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R

@Composable
fun PinSetupScreen(
    state: PinSetupUiState,
    onSubmit: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    // Keyed on the step so the field clears between EnterNew and
    // ConfirmNew rather than carrying the previous digits forward.
    var input by remember(state.step) { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = titleFor(state.mode, state.step)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 8) input = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.applock_pin_hint)) },
                singleLine = true,
                isError = state.errorMessage != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = state.errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (state.isSaving) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { onSubmit(input) },
                    enabled = input.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.applock_continue_action))
                }
            }
        }
    }
}

@Composable
private fun titleFor(mode: PinSetupMode, step: PinSetupStep): String = when {
    step == PinSetupStep.EnterCurrent -> stringResource(R.string.applock_enter_current_pin)
    mode == PinSetupMode.DISABLE -> stringResource(R.string.applock_enter_current_pin)
    step == PinSetupStep.ConfirmNew -> stringResource(R.string.applock_confirm_new_pin)
    else -> stringResource(R.string.applock_enter_new_pin)
}
