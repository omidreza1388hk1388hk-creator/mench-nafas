package com.omidgame.mench.feature.security.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Rendered as a full-screen overlay above the nav graph (see MainActivity)
 * rather than a nav destination — it must be able to appear over whatever
 * screen the user was already on, and must intercept the back gesture
 * implicitly by simply covering everything, not by owning a back stack
 * entry that could be popped around.
 */
@Composable
fun LockScreen(
    state: LockUiState,
    onSubmitPin: (String) -> Unit,
    onBiometricRequested: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(state.biometricAvailable) {
        if (state.biometricAvailable) onBiometricRequested()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.height(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.applock_locked_title), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8) pin = it.filter(Char::isDigit) },
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
            if (state.isVerifying) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { onSubmitPin(pin) },
                    enabled = pin.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.applock_unlock_action))
                }
                if (state.biometricAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    IconButton(onClick = onBiometricRequested) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = stringResource(R.string.applock_use_biometric),
                        )
                    }
                }
            }
        }
    }
}
