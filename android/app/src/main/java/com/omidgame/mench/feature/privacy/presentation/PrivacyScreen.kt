package com.omidgame.mench.feature.privacy.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.network.UpdatePrivacyBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    state: PrivacyUiState,
    onChange: (UpdatePrivacyBody) -> Unit,
    onRetry: () -> Unit,
    onTwoStepClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            is PrivacyUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is PrivacyUiState.Error -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.privacy_load_failed), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.privacy_retry)) }
            }
            is PrivacyUiState.Content -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            ) {
                val s = state.settings
                if (state.saveFailed) {
                    Text(
                        text = stringResource(R.string.privacy_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                VisibilityRow(
                    title = stringResource(R.string.privacy_last_seen),
                    hint = stringResource(R.string.privacy_last_seen_hint),
                    value = s.lastSeen,
                    onSelect = { onChange(UpdatePrivacyBody(lastSeen = it)) },
                )
                VisibilityRow(
                    title = stringResource(R.string.privacy_profile_photo),
                    hint = null,
                    value = s.profilePhoto,
                    onSelect = { onChange(UpdatePrivacyBody(profilePhoto = it)) },
                )
                VisibilityRow(
                    title = stringResource(R.string.privacy_add_me),
                    hint = stringResource(R.string.privacy_add_me_hint),
                    value = s.addMe,
                    onSelect = { onChange(UpdatePrivacyBody(addMe = it)) },
                )
                VisibilityRow(
                    title = stringResource(R.string.privacy_calls),
                    hint = null,
                    value = s.calls,
                    onSelect = { onChange(UpdatePrivacyBody(calls = it)) },
                )
                SwitchRow(
                    title = stringResource(R.string.privacy_read_receipts),
                    hint = stringResource(R.string.privacy_read_receipts_hint),
                    checked = s.readReceipts,
                    onCheckedChange = { onChange(UpdatePrivacyBody(readReceipts = it)) },
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onTwoStepClick, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.two_step_title))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilityRow(title: String, hint: String?, value: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = value == "everybody",
                onClick = { onSelect("everybody") },
                label = { Text(stringResource(R.string.privacy_everybody)) },
            )
            FilterChip(
                selected = value == "nobody",
                onClick = { onSelect("nobody") },
                label = { Text(stringResource(R.string.privacy_nobody)) },
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
