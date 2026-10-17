package com.omidgame.mench.feature.auth.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R

/** Second login step for accounts with two-step verification: the SMS code was right, now the password. */
@Composable
fun TwoStepEntryScreen(
    state: AuthUiState.TwoStepEntry,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AuthBackdrop {
        Text(
            text = stringResource(R.string.two_step_login_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.two_step_login_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        state.challenge.hint?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.two_step_login_hint, it),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(64) },
            label = { Text(stringResource(R.string.two_step_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty() && !state.isVerifying) onSubmit(password) }),
            enabled = !state.isVerifying,
            isError = state.errorReason != null,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.6f),
                errorTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                errorBorderColor = Color(0xFFFFB4AB),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.75f),
                errorLabelColor = Color(0xFFFFB4AB),
                cursorColor = Color.White,
                errorCursorColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        state.errorReason?.let { reason ->
            Spacer(Modifier.height(8.dp))
            Text(text = authErrorText(reason), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.two_step_login_continue),
            enabled = password.isNotEmpty(),
            loading = state.isVerifying,
            onClick = { onSubmit(password) },
        )
    }
}
