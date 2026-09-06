package com.omidgame.mench.feature.security.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.omidgame.mench.R
import com.omidgame.mench.feature.security.domain.AppLockTimeoutOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecurityScreen(
    state: PrivacySecurityUiState,
    onResumed: () -> Unit,
    onAppLockEnableRequested: () -> Unit,
    onAppLockDisableRequested: () -> Unit,
    onChangePinRequested: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onTimeoutSelected: (AppLockTimeoutOption) -> Unit,
    onLogoutAllDevices: () -> Unit,
    onLoggedOutAll: () -> Unit,
    onBackClick: () -> Unit,
) {
    // Re-reads app-lock state whenever this screen comes back into the
    // foreground — necessary because setting/changing/disabling the PIN
    // happens on a separate screen (PinSetupScreen) that this one
    // navigates to and back from, and the ViewModel's own state would
    // otherwise go stale the moment that flow completes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.loggedOutAll) {
        if (state.loggedOutAll) onLoggedOutAll()
    }

    var showLogoutAllConfirm by remember { mutableStateOf(false) }

    if (showLogoutAllConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutAllConfirm = false },
            title = { Text(stringResource(R.string.privacy_logout_all_confirm_title)) },
            text = { Text(stringResource(R.string.privacy_logout_all_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { showLogoutAllConfirm = false; onLogoutAllDevices() }) {
                    Text(stringResource(R.string.privacy_logout_all_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutAllConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_privacy_security)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.applock_toggle_title)) },
                supportingContent = { Text(stringResource(R.string.applock_toggle_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = state.appLockEnabled,
                        onCheckedChange = { checked ->
                            if (checked) onAppLockEnableRequested() else onAppLockDisableRequested()
                        },
                    )
                },
            )

            if (state.appLockEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.applock_change_pin)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                    OutlinedButton(onClick = onChangePinRequested) {
                        Text(stringResource(R.string.applock_change_pin_action))
                    }
                }

                if (state.biometricAvailableOnDevice) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.applock_biometric_toggle)) },
                        trailingContent = {
                            Switch(checked = state.biometricEnabled, onCheckedChange = onBiometricToggle)
                        },
                    )
                }

                Divider()
                ListItem(headlineContent = { Text(stringResource(R.string.applock_timeout_title)) })
                AppLockTimeoutOption.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(labelFor(option)) },
                        leadingContent = {
                            RadioButton(
                                selected = option == state.autoLockTimeout,
                                onClick = { onTimeoutSelected(option) },
                            )
                        },
                    )
                }
            }

            if (state.infoMessage != null) {
                Text(
                    text = state.infoMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.privacy_sessions_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showLogoutAllConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoggingOutAll,
                ) {
                    Text(stringResource(R.string.privacy_logout_all_action))
                }
            }
        }
    }
}

@Composable
private fun labelFor(option: AppLockTimeoutOption): String = when (option) {
    AppLockTimeoutOption.IMMEDIATE -> stringResource(R.string.applock_timeout_immediate)
    AppLockTimeoutOption.AFTER_30_SECONDS -> stringResource(R.string.applock_timeout_30s)
    AppLockTimeoutOption.AFTER_1_MINUTE -> stringResource(R.string.applock_timeout_1m)
    AppLockTimeoutOption.AFTER_5_MINUTES -> stringResource(R.string.applock_timeout_5m)
    AppLockTimeoutOption.AFTER_15_MINUTES -> stringResource(R.string.applock_timeout_15m)
}
