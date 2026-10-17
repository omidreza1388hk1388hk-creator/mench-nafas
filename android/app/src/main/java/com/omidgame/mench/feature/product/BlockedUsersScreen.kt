package com.omidgame.mench.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.omidgame.mench.core.network.BlockedUserResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockedUsersUiState(
    val loading: Boolean = true,
    val users: List<BlockedUserResponse> = emptyList(),
    val error: Boolean = false,
)

@HiltViewModel
class BlockedUsersViewModel @Inject constructor(private val repo: ProductRepository) : ViewModel() {
    private val _state = MutableStateFlow(BlockedUsersUiState())
    val state: StateFlow<BlockedUsersUiState> = _state

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            repo.blocked()
                .onSuccess { list -> _state.update { it.copy(loading = false, users = list) } }
                .onFailure { _state.update { s -> s.copy(loading = false, error = true) } }
        }
    }

    fun unblock(userId: String) {
        viewModelScope.launch {
            repo.unblock(userId).onSuccess { _state.update { s -> s.copy(users = s.users.filterNot { it.id == userId }) } }
        }
    }
}

@Composable
fun BlockedUsersScreen(onBack: () -> Unit, viewModel: BlockedUsersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProductScaffold(title = stringResource(R.string.blocked_title), onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.product_error_generic), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.message_retry)) }
                }
                state.users.isEmpty() -> Text(
                    stringResource(R.string.blocked_empty),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.users, key = { it.id }) { user ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(user.displayName ?: user.username ?: user.phoneE164, style = MaterialTheme.typography.titleMedium)
                                user.username?.let { Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            OutlinedButton(onClick = { viewModel.unblock(user.id) }) { Text(stringResource(R.string.blocked_unblock)) }
                        }
                    }
                }
            }
        }
    }
}
