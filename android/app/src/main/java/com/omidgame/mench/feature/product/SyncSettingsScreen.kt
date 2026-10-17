package com.omidgame.mench.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.omidgame.mench.R
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.omidgame.mench.feature.chat.presentation.DraftSync

/** Settings > Sync: opt in to sharing unsent drafts between your own devices. */
@Composable
fun SyncSettingsScreen(onBack: () -> Unit, viewModel: SyncSettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val context = LocalContext.current
    var drafts by remember { mutableStateOf(DraftSync.isEnabled(context)) }
    var appearance by remember { mutableStateOf(viewModel.appearanceEnabled) }
    ProductScaffold(title = stringResource(R.string.sync_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.sync_drafts_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.sync_drafts_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = drafts, onCheckedChange = { drafts = it; DraftSync.setEnabled(context, it) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.sync_appearance_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.sync_appearance_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = appearance, onCheckedChange = { appearance = it; viewModel.setAppearanceEnabled(it) })
            }
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class SyncSettingsViewModel @javax.inject.Inject constructor(
    private val appearanceSync: com.omidgame.mench.feature.appearance.data.AppearanceSync,
) : androidx.lifecycle.ViewModel() {
    val appearanceEnabled: Boolean get() = appearanceSync.enabled

    /** Turning it on first adopts what another device already saved, otherwise uploads this phone's look. */
    fun setAppearanceEnabled(value: Boolean) {
        appearanceSync.enabled = value
        if (value) {
            viewModelScope.launch {
                if (!appearanceSync.pullIfEnabled()) appearanceSync.pushIfEnabled()
            }
        }
    }
}
