package com.omidgame.mench.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.accounts.AccountEntry
import com.omidgame.mench.core.accounts.AccountRegistry
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.core.ui.theme.MenchBrand

/**
 * Account switcher. Only the ACTIVE account's data is open at a time, so unread counts and push
 * notifications for the other accounts are not available (they appear when you switch to them).
 */
@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var version by remember { mutableStateOf(0) }
    val active = remember(version) { AccountRegistry.activeSlot(context) }
    val accounts = remember(version) { AccountRegistry.list(context) }
    var switchTarget by remember { mutableStateOf<AccountEntry?>(null) }
    var removeTarget by remember { mutableStateOf<AccountEntry?>(null) }
    var confirmAdd by remember { mutableStateOf(false) }

    ProductScaffold(title = stringResource(R.string.accounts_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.accounts_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts, key = { it.slot }) { acc ->
                    val isActive = acc.slot == active
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(MenchBrand.heroBrush()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text((acc.displayName ?: acc.username ?: "?").take(1).uppercase(), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(acc.displayName ?: stringResource(R.string.accounts_unnamed), style = MaterialTheme.typography.titleSmall)
                                acc.username?.let { Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            when {
                                isActive -> Text(stringResource(R.string.accounts_active), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                else -> Row {
                                    TextButton(onClick = { switchTarget = acc }) { Text(stringResource(R.string.accounts_switch)) }
                                    if (acc.slot != AccountRegistry.DEFAULT_SLOT) TextButton(onClick = { removeTarget = acc }) { Text(stringResource(R.string.accounts_remove)) }
                                }
                            }
                        }
                    }
                }
            }
            Button(onClick = { confirmAdd = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.accounts_add)) }
        }
    }

    switchTarget?.let { acc ->
        MenchDialog(
            onDismissRequest = { switchTarget = null },
            title = { Text(stringResource(R.string.accounts_switch)) },
            text = { Text(stringResource(R.string.accounts_switch_body)) },
            confirmButton = { TextButton(onClick = { AccountRegistry.switchAndRestart(context, acc.slot) }) { Text(stringResource(R.string.permission_continue)) } },
            dismissButton = { TextButton(onClick = { switchTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmAdd) {
        MenchDialog(
            onDismissRequest = { confirmAdd = false },
            title = { Text(stringResource(R.string.accounts_add)) },
            text = { Text(stringResource(R.string.accounts_add_body)) },
            confirmButton = { TextButton(onClick = { AccountRegistry.switchAndRestart(context, AccountRegistry.createSlot(context)) }) { Text(stringResource(R.string.permission_continue)) } },
            dismissButton = { TextButton(onClick = { confirmAdd = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    removeTarget?.let { acc ->
        MenchDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.accounts_remove)) },
            text = { Text(stringResource(R.string.accounts_remove_body)) },
            confirmButton = { TextButton(onClick = { AccountRegistry.removeSlot(context, acc.slot); removeTarget = null; version++ }) { Text(stringResource(R.string.accounts_remove)) } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
