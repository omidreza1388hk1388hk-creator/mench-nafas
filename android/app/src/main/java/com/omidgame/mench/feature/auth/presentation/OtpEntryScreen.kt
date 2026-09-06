package com.omidgame.mench.feature.auth.presentation

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R

@Composable
fun OtpEntryScreen(
    state: AuthUiState.OtpEntry,
    onSubmitCode: (String) -> Unit,
    onResend: () -> Unit,
) {
    var code by remember(state.challenge.challengeId) { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.auth_otp_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(stringResource(R.string.auth_otp_hint)) },
            singleLine = true,
            isError = state.errorMessage != null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = state.errorMessage, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (state.isVerifying) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onSubmitCode(code) },
                modifier = Modifier.fillMaxWidth(),
                enabled = code.isNotBlank(),
            ) {
                Text(stringResource(R.string.auth_otp_verify))
            }
            TextButton(
                onClick = onResend,
                enabled = state.resendCooldownSeconds == 0,
            ) {
                Text(stringResource(R.string.auth_otp_resend))
            }
        }
    }
}
