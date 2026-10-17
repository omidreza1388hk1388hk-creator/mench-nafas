package com.omidgame.mench.feature.devices.presentation

import androidx.compose.foundation.background
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.menchTopBarColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import com.omidgame.mench.core.ui.components.MenchDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.text.format.DateUtils
import com.omidgame.mench.R
import com.omidgame.mench.feature.devices.domain.DeviceSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagementScreen(
    state: DeviceManagementUiState,
    onRevokeDevice: (String) -> Unit,
    onBackClick: () -> Unit,
    onRevokeOthers: () -> Unit = {},
) {
    var confirmOthers by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<DeviceSession?>(null) }

    pendingRevoke?.let { device ->
        MenchDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text(stringResource(R.string.devices_revoke_confirm_title)) },
            text = { Text(stringResource(R.string.devices_revoke_confirm_body, device.deviceName)) },
            confirmButton = {
                TextButton(onClick = {
                    onRevokeDevice(device.id)
                    pendingRevoke = null
                }) {
                    Text(stringResource(R.string.devices_revoke_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmOthers) {
        MenchDialog(
            onDismissRequest = { confirmOthers = false },
            title = { Text(stringResource(R.string.sx_dev_revoke_others_title)) },
            text = { Text(stringResource(R.string.sx_dev_revoke_others_body)) },
            confirmButton = {
                TextButton(onClick = { confirmOthers = false; onRevokeOthers() }) {
                    Text(stringResource(R.string.sx_dev_revoke_others_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmOthers = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = menchTopBarColors(),
                title = { Text(stringResource(R.string.devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.errorMessage != null -> Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.devices.isEmpty() -> Text(
                    text = stringResource(R.string.devices_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn {
                    if (state.devices.any { !it.isCurrent }) {
                        item {
                            TextButton(
                                onClick = { confirmOthers = true },
                                enabled = !state.isRevokingOthers,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                Text(stringResource(R.string.sx_dev_revoke_others), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    items(state.devices, key = { it.id }) { device ->
                        DeviceRow(
                            device = device,
                            isRevoking = state.revokingDeviceId == device.id,
                            onRevokeClick = { pendingRevoke = device },
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceSession, isRevoking: Boolean, onRevokeClick: () -> Unit) {
    val subtitle = remember(device.lastSeenAtEpochMillis, device.platform) {
        val lastSeen = device.lastSeenAtEpochMillis?.let {
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        }
        listOfNotNull(device.platform, lastSeen).joinToString(" · ")
    }
    val thisDevice = stringResource(R.string.sx_dev_this)

    ListItem(
        headlineContent = { Text(if (device.isCurrent) "${device.deviceName} · $thisDevice" else device.deviceName) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = if (device.platform.equals("android", ignoreCase = true)) {
                    Icons.Filled.PhoneAndroid
                } else {
                    Icons.Filled.Computer
                },
                contentDescription = null,
            )
        },
        trailingContent = {
            if (device.isCurrent) {
                // This device is logged out with the normal log-out button, not from this list.
            } else if (isRevoking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = onRevokeClick) {
                    Icon(
                        Icons.Filled.Logout,
                        contentDescription = stringResource(R.string.devices_revoke_action),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}
