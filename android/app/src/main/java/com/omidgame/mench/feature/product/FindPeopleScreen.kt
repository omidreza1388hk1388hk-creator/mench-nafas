package com.omidgame.mench.feature.product

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.network.InviteResponse
import com.omidgame.mench.core.network.PublicUserResponse
import com.omidgame.mench.feature.chat.domain.ChatResult
import com.omidgame.mench.feature.chat.domain.StartDirectConversationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FindPeopleUiState(
    val searching: Boolean = false,
    val result: PublicUserResponse? = null,
    val notFound: Boolean = false,
    val invite: InviteResponse? = null,
    val openChatId: String? = null,
    val error: Boolean = false,
)

@HiltViewModel
class FindPeopleViewModel @Inject constructor(
    private val repo: ProductRepository,
    private val startDirect: StartDirectConversationUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(FindPeopleUiState())
    val state: StateFlow<FindPeopleUiState> = _state

    init {
        viewModelScope.launch { repo.myInvite().onSuccess { inv -> _state.update { it.copy(invite = inv) } } }
    }

    fun searchUsername(raw: String) {
        if (raw.isBlank()) return
        _state.update { it.copy(searching = true, notFound = false, error = false, result = null) }
        viewModelScope.launch {
            repo.byUsername(raw.trim())
                .onSuccess { u -> _state.update { it.copy(searching = false, result = u) } }
                .onFailure { e ->
                    val missing = (e as? retrofit2.HttpException)?.code() in setOf(400, 404)
                    _state.update { it.copy(searching = false, notFound = missing, error = !missing) }
                }
        }
    }

    /** Called with a deep link / scanned QR payload. */
    fun resolveInviteCode(code: String) {
        _state.update { it.copy(searching = true, notFound = false, error = false, result = null) }
        viewModelScope.launch {
            repo.resolveInvite(code)
                .onSuccess { u -> _state.update { it.copy(searching = false, result = u) } }
                .onFailure { _state.update { s -> s.copy(searching = false, notFound = true) } }
            repo.track("qr_scanned")
        }
    }

    fun message(user: PublicUserResponse) {
        viewModelScope.launch {
            when (val r = startDirect(user.phoneE164)) {
                is ChatResult.Success -> _state.update { it.copy(openChatId = r.value.id) }
                is ChatResult.Failure -> _state.update { it.copy(error = true) }
            }
        }
    }

    fun chatOpened() { _state.update { it.copy(openChatId = null) } }
    fun inviteShared() { viewModelScope.launch { repo.track("invite_shared") } }
}

@Composable
fun FindPeopleScreen(
    initialInviteCode: String?,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: FindPeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var smsPhone by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }

    LaunchedEffect(initialInviteCode) { initialInviteCode?.let(viewModel::resolveInviteCode) }
    LaunchedEffect(state.openChatId) {
        state.openChatId?.let { onOpenChat(it); viewModel.chatOpened() }
    }

    ProductScaffold(title = stringResource(R.string.find_people_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // --- username search ---
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.removePrefix("@") },
                    label = { Text(stringResource(R.string.find_people_username_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { viewModel.searchUsername(username) }, enabled = !state.searching) {
                    Text(stringResource(R.string.find_people_search))
                }
            }
            state.result?.let { user ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(user.displayName ?: user.username ?: user.phoneE164, style = MaterialTheme.typography.titleMedium)
                        user.username?.let { Text("@$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        user.bio?.takeIf { it.isNotBlank() }?.let { Text(it, modifier = Modifier.padding(top = 6.dp)) }
                        Button(onClick = { viewModel.message(user) }, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.find_people_message))
                        }
                    }
                }
            }
            if (state.notFound) Text(stringResource(R.string.find_people_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.error) Text(stringResource(R.string.product_error_generic), color = MaterialTheme.colorScheme.error)

            // --- QR ---
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showQr = !showQr }, modifier = Modifier.weight(1f), enabled = state.invite != null) {
                    Text(stringResource(R.string.find_people_my_qr))
                }
                OutlinedButton(onClick = onScanQr, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.find_people_scan_qr))
                }
            }
            val invite = state.invite
            if (showQr && invite != null) {
                val bitmap = remember(invite.link) { QrCodes.generate(invite.deepLink) }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = stringResource(R.string.find_people_my_qr), modifier = Modifier.size(240.dp))
                    Text(invite.code, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // --- invite link + SMS ---
            if (invite != null) {
                val message = stringResource(R.string.find_people_invite_message, invite.link)
                Button(
                    onClick = {
                        viewModel.inviteShared()
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message)
                        }, null))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.find_people_share_link)) }

                OutlinedTextField(
                    value = smsPhone,
                    onValueChange = { smsPhone = it },
                    label = { Text(stringResource(R.string.find_people_sms_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        viewModel.inviteShared()
                        // The user's own SMS app sends it; MENCH never reads or sends SMS itself.
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${smsPhone.trim()}")).apply {
                            putExtra("sms_body", message)
                        })
                    },
                    enabled = smsPhone.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.find_people_invite_sms)) }
            }
        }
    }
}
