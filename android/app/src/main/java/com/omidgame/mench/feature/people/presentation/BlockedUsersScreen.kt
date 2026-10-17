package com.omidgame.mench.feature.people.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.core.ui.components.UserAvatar
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.menchTopBarColors
import com.omidgame.mench.feature.people.data.PublicUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    state: BlockedUsersUiState,
    onRetry: () -> Unit,
    onUnblock: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    var pendingUnblock by remember { mutableStateOf<PublicUser?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = menchTopBarColors(),
                title = { Text(stringResource(R.string.blocked_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.errorOffline || state.errorGeneric -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(if (state.errorOffline) R.string.find_offline else R.string.find_failed),
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRetry) { Text(stringResource(R.string.message_retry)) }
                }
                state.users.isEmpty() -> Text(
                    text = stringResource(R.string.blocked_empty),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.users, key = { it.id }) { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(userId = user.id, version = user.avatarVersion, name = user.displayName ?: user.username)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.displayName ?: user.username.orEmpty(), fontWeight = FontWeight.Medium)
                                if (user.username != null) {
                                    Text(
                                        "@" + user.username,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = { pendingUnblock = user }) { Text(stringResource(R.string.unblock_action)) }
                        }
                    }
                }
            }
        }
    }

    pendingUnblock?.let { user ->
        MenchDialog(
            onDismissRequest = { pendingUnblock = null },
            title = { Text(stringResource(R.string.unblock_confirm_title, user.displayName ?: user.username.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    onUnblock(user.id)
                    pendingUnblock = null
                }) { Text(stringResource(R.string.unblock_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnblock = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
