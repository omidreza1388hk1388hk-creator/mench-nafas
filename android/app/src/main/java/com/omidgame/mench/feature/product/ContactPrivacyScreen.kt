package com.omidgame.mench.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.contacts.ContactSyncPrefs
import com.omidgame.mench.core.network.ContactPrivacyBody
import com.omidgame.mench.core.network.UsersApi
import com.omidgame.mench.core.ui.theme.MenchDigits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class ContactPrivacyUiState(
    val syncEnabled: Boolean = true,
    val discoverable: Boolean = true,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastMatchCount: Int = 0,
)

@HiltViewModel
class ContactPrivacyViewModel @Inject constructor(
    private val usersApi: UsersApi,
    private val prefs: ContactSyncPrefs,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ContactPrivacyUiState(
            syncEnabled = prefs.syncEnabled,
            lastSyncAt = prefs.lastSyncAtEpochMillis,
            lastMatchCount = prefs.lastMatchCount,
        ),
    )
    val state: StateFlow<ContactPrivacyUiState> = _state

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            runCatching { usersApi.getContactPrivacy() }
                .onSuccess { r -> _state.update { it.copy(loading = false, discoverable = r.discoverable) } }
                .onFailure { _state.update { s -> s.copy(loading = false, error = true) } }
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        prefs.syncEnabled = enabled
        _state.update { it.copy(syncEnabled = enabled) }
    }

    fun setDiscoverable(value: Boolean) {
        val previous = _state.value.discoverable
        _state.update { it.copy(discoverable = value, saving = true, error = false) }
        viewModelScope.launch {
            runCatching { usersApi.updateContactPrivacy(ContactPrivacyBody(value)) }
                .onSuccess { r -> _state.update { it.copy(discoverable = r.discoverable, saving = false) } }
                // Roll the switch back so the screen never claims a privacy state the server does not have.
                .onFailure { _state.update { s -> s.copy(discoverable = previous, saving = false, error = true) } }
        }
    }

    fun forgetSyncInfo() {
        prefs.clearBookkeeping()
        _state.update { it.copy(lastSyncAt = 0L, lastMatchCount = 0) }
    }
}

@Composable
fun ContactPrivacyScreen(onBack: () -> Unit, viewModel: ContactPrivacyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProductScaffold(title = stringResource(R.string.contact_privacy_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PrivacySwitchRow(
                title = stringResource(R.string.contact_privacy_sync_title),
                body = stringResource(R.string.contact_privacy_sync_body),
                checked = state.syncEnabled,
                enabled = true,
                onCheckedChange = viewModel::setSyncEnabled,
            )
            PrivacySwitchRow(
                title = stringResource(R.string.contact_privacy_discoverable_title),
                body = stringResource(R.string.contact_privacy_discoverable_body),
                checked = state.discoverable,
                enabled = !state.loading && !state.saving,
                onCheckedChange = viewModel::setDiscoverable,
            )
            if (state.error) {
                Text(
                    stringResource(R.string.product_error_generic),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.lastSyncAt > 0L) {
                val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.lastSyncAt))
                Text(
                    MenchDigits.toPersian(stringResource(R.string.contact_privacy_last_sync, whenText, state.lastMatchCount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = viewModel::forgetSyncInfo) {
                    Text(stringResource(R.string.contact_privacy_forget))
                }
            }
            Text(
                stringResource(R.string.contact_privacy_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
