package com.omidgame.mench.feature.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPrivacyScreen(
    state: NotificationPrivacyUiState,
    onSelect: (NotificationPrivacyMode) -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notification_privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is NotificationPrivacyUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is NotificationPrivacyUiState.Error -> {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is NotificationPrivacyUiState.Content -> {
                    Text(
                        text = stringResource(R.string.settings_notification_privacy_description),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PrivacyOptionRow(
                        label = stringResource(R.string.settings_notification_privacy_full_content),
                        selected = state.selected == NotificationPrivacyMode.FULL_CONTENT,
                        onClick = { onSelect(NotificationPrivacyMode.FULL_CONTENT) },
                    )
                    PrivacyOptionRow(
                        label = stringResource(R.string.settings_notification_privacy_sender_only),
                        selected = state.selected == NotificationPrivacyMode.SENDER_ONLY,
                        onClick = { onSelect(NotificationPrivacyMode.SENDER_ONLY) },
                    )
                    PrivacyOptionRow(
                        label = stringResource(R.string.settings_notification_privacy_hide_content),
                        selected = state.selected == NotificationPrivacyMode.HIDE_CONTENT,
                        onClick = { onSelect(NotificationPrivacyMode.HIDE_CONTENT) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(text = label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
