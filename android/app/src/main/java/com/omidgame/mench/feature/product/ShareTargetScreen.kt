package com.omidgame.mench.feature.product

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.chat.domain.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareTargetViewModel @Inject constructor(private val chatRepository: ChatRepository) : ViewModel() {
    val conversations: StateFlow<List<Conversation>> = chatRepository.observeConversations()
        .map { list -> list.filter { !it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun send(context: Context, draft: ShareDraft, conversationId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val stream = draft.streamUri
            if (stream != null) {
                val uri = Uri.parse(stream)
                val mime = draft.mimeType?.takeIf { !it.endsWith("/*") } ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "shared"
                chatRepository.sendAttachment(conversationId, stream, mime, name, draft.text)
            } else if (!draft.text.isNullOrBlank()) {
                chatRepository.sendMessage(conversationId, draft.text)
            }
            ShareInbox.clear()
            onDone()
        }
    }
}

/** "Share to MENCH": pick one chat, confirm, send. Opens the chat afterwards. */
@Composable
fun ShareTargetScreen(onBack: () -> Unit, onSent: (String) -> Unit, viewModel: ShareTargetViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val draft = remember { ShareInbox.peek() }
    var selected by remember { mutableStateOf(draft?.preselectedConversationId) }

    LaunchedEffect(draft) { if (draft == null) onBack() }

    ProductScaffold(title = stringResource(R.string.share_title), onBack = { ShareInbox.clear(); onBack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            draft?.text?.takeIf { it.isNotBlank() }?.let {
                Text(it.take(200), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { selected = c.id }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == c.id, onClick = { selected = c.id })
                        Text(c.title ?: c.otherUserDisplayName ?: c.otherUserPhoneE164 ?: "—", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Button(
                onClick = { val id = selected; if (id != null && draft != null) viewModel.send(context, draft, id) { onSent(id) } },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(stringResource(R.string.share_send)) }
        }
    }
}
