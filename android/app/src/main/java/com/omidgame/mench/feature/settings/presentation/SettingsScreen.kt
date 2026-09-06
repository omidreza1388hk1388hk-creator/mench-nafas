package com.omidgame.mench.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.omidgame.mench.BuildConfig
import com.omidgame.mench.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAccountClick: () -> Unit,
    onPrivacySecurityClick: () -> Unit,
    onDataStorageClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {},
    onLogout: () -> Unit,
    onLoggedOut: () -> Unit,
    onBackClick: () -> Unit,
) {
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                headlineContent = { Text(state.displayName ?: state.phoneE164 ?: "") },
                supportingContent = { if (state.displayName != null) Text(state.phoneE164 ?: "") },
                leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_account)) },
                leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onAccountClick),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_security)) },
                leadingContent = { Icon(Icons.Filled.Shield, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onPrivacySecurityClick),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_data_storage)) },
                leadingContent = { Icon(Icons.Filled.Storage, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onDataStorageClick),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onNotificationsClick),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.diagnostics_title)) },
                leadingContent = { Icon(Icons.Filled.HealthAndSafety, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onDiagnosticsClick),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
            )
            if (state.isLoggingOut) {
                ListItem(headlineContent = { CircularProgressIndicator() })
            } else {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_logout), color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onLogout),
                )
            }
        }
    }
}
