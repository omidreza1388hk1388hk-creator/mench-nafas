package com.omidgame.mench.feature.chat.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.omidgame.mench.R
import com.omidgame.mench.core.media.PlaybackState
import com.omidgame.mench.core.media.resolveContentUriMeta
import com.omidgame.mench.core.network.AttachmentUrls
import com.omidgame.mench.feature.chat.domain.Conversation
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import com.omidgame.mench.feature.chat.domain.DomainMessageState
import com.omidgame.mench.feature.chat.domain.Message
import com.omidgame.mench.feature.chat.domain.MessageAttachment
import com.omidgame.mench.feature.chat.domain.ReactionSummary

/** Quick-react presets shown on long-press — not meant to be exhaustive (spec section 20's proprietary MENCH emoji system is a later phase); these six cover the common WhatsApp/Telegram-style reaction set. */
private val QUICK_REACTIONS = listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F")

@Composable
fun ChatScreen(
    state: ChatUiState,
    isRecording: Boolean,
    playbackState: PlaybackState,
    onSend: (String) -> Unit,
    onSendAttachment: (contentUri: String, mimeType: String, originalFilename: String) -> Unit,
    onStartRecording: () -> Boolean,
    onStopRecordingAndSend: () -> Unit,
    onCancelRecording: () -> Unit,
    onTogglePlayback: (sourceId: String, localPath: String?, remoteUrl: String?) -> Unit,
    onMessagesRead: (Long) -> Unit,
    onStartEdit: (Message) -> Unit,
    onCancelEdit: () -> Unit,
    onSubmitEdit: (String) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onReact: (Message, String) -> Unit,
    onForward: (Message, String) -> Unit,
    onBackClick: () -> Unit,
    onGroupInfoClick: () -> Unit,
    onVoiceCallClick: () -> Unit = {},
    onVideoCallClick: () -> Unit = {},
) {
    var viewerAttachment by remember { mutableStateOf<MessageAttachment?>(null) }
    var playerAttachment by remember { mutableStateOf<MessageAttachment?>(null) }
    var forwardTarget by remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(state.messages) {
        val highestIncomingSequence = state.messages
            .asSequence()
            .filter { !it.isOwn }
            .mapNotNull { it.sequence }
            .maxOrNull()
        if (highestIncomingSequence != null) {
            onMessagesRead(highestIncomingSequence)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.headerTitle ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
                actions = {
                    // Calls are 1:1 only (see backend CallsService.initiate) —
                    // a group conversation has no single "other member" to
                    // call, so these only make sense for a direct chat.
                    if (!state.isGroupConversation) {
                        IconButton(onClick = onVoiceCallClick) {
                            Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.chat_voice_call))
                        }
                        IconButton(onClick = onVideoCallClick) {
                            Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.chat_video_call))
                        }
                    }
                    // Only a group has anything to show on this screen —
                    // a direct conversation has no roster, no title to
                    // rename, nothing Group Info would display.
                    if (state.isGroupConversation) {
                        IconButton(onClick = onGroupInfoClick) {
                            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.chat_group_info))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                items(state.messages, key = { it.clientMsgId }) { message ->
                    MessageBubble(
                        message = message,
                        // Only shown for a received message in a group — your
                        // own messages need no label, and a direct
                        // conversation only ever has one other sender anyway
                        // (see ChatUiState.isGroupConversation).
                        senderLabel = if (state.isGroupConversation && !message.isOwn) {
                            state.memberDisplayNames[message.senderId]
                        } else {
                            null
                        },
                        playbackState = playbackState,
                        onImageClick = { viewerAttachment = it },
                        onVideoClick = { playerAttachment = it },
                        onTogglePlayback = onTogglePlayback,
                        onStartEdit = onStartEdit,
                        onDeleteMessage = onDeleteMessage,
                        onReact = onReact,
                        onForwardRequested = { forwardTarget = it },
                    )
                }
                if (state.isOtherTyping) {
                    item {
                        Text(
                            text = stringResource(R.string.chat_typing_indicator),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            Composer(
                isRecording = isRecording,
                editingMessage = state.editingMessage,
                onSend = onSend,
                onSendAttachment = onSendAttachment,
                onStartRecording = onStartRecording,
                onStopRecordingAndSend = onStopRecordingAndSend,
                onCancelRecording = onCancelRecording,
                onCancelEdit = onCancelEdit,
                onSubmitEdit = onSubmitEdit,
            )
        }
    }

    viewerAttachment?.let { attachment ->
        ImageViewerDialog(attachment = attachment, onDismiss = { viewerAttachment = null })
    }
    playerAttachment?.let { attachment ->
        VideoPlayerDialog(attachment = attachment, onDismiss = { playerAttachment = null })
    }
    forwardTarget?.let { target ->
        ForwardDialog(
            conversations = state.conversations.filter { it.id != target.conversationId },
            onSelect = { destinationConversationId ->
                onForward(target, destinationConversationId)
                forwardTarget = null
            },
            onDismiss = { forwardTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    senderLabel: String?,
    playbackState: PlaybackState,
    onImageClick: (MessageAttachment) -> Unit,
    onVideoClick: (MessageAttachment) -> Unit,
    onTogglePlayback: (sourceId: String, localPath: String?, remoteUrl: String?) -> Unit,
    onStartEdit: (Message) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onReact: (Message, String) -> Unit,
    onForwardRequested: (Message) -> Unit,
) {
    val alignment = if (message.isOwn) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isOwn) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val isDeleted = message.deletedAtEpochMillis != null
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = alignment) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {},
                        // A deleted message has nothing left worth acting
                        // on (no re-editing a deleted message, no
                        // reacting to it) — see MessagesRepository.react's
                        // matching server-side deleted_at check.
                        onLongClick = { if (!isDeleted) menuExpanded = true },
                    )
                    .background(bubbleColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (senderLabel != null) {
                    Text(
                        text = senderLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isDeleted) {
                    Text(
                        text = stringResource(R.string.chat_message_deleted),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    if (message.forwardedFromMessageId != null) {
                        Text(
                            text = stringResource(R.string.chat_forwarded_label),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    when (message.kind) {
                        DomainMessageKind.IMAGE -> message.attachment?.let { attachment ->
                            ImageAttachmentContent(attachment, onClick = { onImageClick(attachment) })
                        }
                        DomainMessageKind.FILE -> message.attachment?.let { attachment ->
                            FileAttachmentContent(attachment)
                        }
                        DomainMessageKind.AUDIO -> message.attachment?.let { attachment ->
                            AudioAttachmentContent(
                                clientMsgId = message.clientMsgId,
                                attachment = attachment,
                                playbackState = playbackState,
                                onTogglePlayback = onTogglePlayback,
                            )
                        }
                        DomainMessageKind.VIDEO -> message.attachment?.let { attachment ->
                            VideoAttachmentContent(attachment, onClick = { onVideoClick(attachment) })
                        }
                        DomainMessageKind.TEXT -> Unit
                    }

                    if (!message.body.isNullOrBlank()) {
                        Text(message.body)
                    }

                    if (message.editedAtEpochMillis != null) {
                        Text(
                            text = stringResource(R.string.chat_message_edited),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    if (message.reactions.isNotEmpty()) {
                        ReactionsRow(reactions = message.reactions, onReactionClick = { onReact(message, it) })
                    }

                    if (message.isOwn) {
                        Text(
                            text = stringResource(stateLabelRes(message.state)),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    QUICK_REACTIONS.forEach { emoji ->
                        Text(
                            text = emoji,
                            modifier = Modifier
                                .clickable {
                                    onReact(message, emoji)
                                    menuExpanded = false
                                }
                                .padding(6.dp),
                        )
                    }
                }
                // Edit is restricted to the sender's own already-sent text
                // messages — a still-PENDING message has no serverId yet
                // (see Message.serverId's doc comment), and only a plain
                // text message's body means anything to edit.
                if (message.isOwn && message.kind == DomainMessageKind.TEXT && message.serverId != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_edit)) },
                        onClick = {
                            onStartEdit(message)
                            menuExpanded = false
                        },
                    )
                }
                if (message.isOwn && message.serverId != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_delete)) },
                        onClick = {
                            onDeleteMessage(message)
                            menuExpanded = false
                        },
                    )
                }
                // Forward is offered on any sent text message, own or
                // received — see MessagesService.forward on the backend
                // for why media messages don't show this option yet.
                if (message.kind == DomainMessageKind.TEXT && message.serverId != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_forward)) },
                        onClick = {
                            onForwardRequested(message)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionsRow(reactions: List<ReactionSummary>, onReactionClick: (String) -> Unit) {
    Row(modifier = Modifier.padding(top = 4.dp)) {
        reactions.forEach { reaction ->
            val background = if (reaction.reactedByMe) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
            Text(
                text = "${reaction.emoji} ${reaction.userIds.size}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .background(background, RoundedCornerShape(10.dp))
                    .clickable { onReactionClick(reaction.emoji) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ForwardDialog(conversations: List<Conversation>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.chat_forward_to), style = MaterialTheme.typography.titleMedium)
            if (conversations.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_no_conversations),
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(conversations, key = { it.id }) { conversation ->
                        Text(
                            text = conversation.otherUserDisplayName
                                ?: conversation.otherUserPhoneE164
                                ?: conversation.id,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(conversation.id) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageAttachmentContent(attachment: MessageAttachment, onClick: () -> Unit) {
    // Prefer the local on-device copy when there is one (this device's own
    // outgoing image, before or regardless of upload completing) — falls
    // back to the server thumbnail URL for received images. Coil handles
    // both a local file path and an authenticated https URL through the
    // same ImageLoader (see core/di/ImageLoaderModule.kt).
    val model = attachment.localContentUri
        ?: attachment.id?.let { AttachmentUrls.thumbnail(it) }

    AsyncImage(
        model = model,
        contentDescription = attachment.originalFilename,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clickable(enabled = attachment.id != null, onClick = onClick),
    )
}

@Composable
private fun FileAttachmentContent(attachment: MessageAttachment) {
    // Informational only for Phase 3a — opening the file in another app
    // (via FileProvider + ACTION_VIEW) is deferred; this renders name/size
    // so the message is legible, not a fully interactive file bubble yet.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.width(28.dp))
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(attachment.originalFilename, style = MaterialTheme.typography.bodyMedium)
            Text(formatFileSize(attachment.sizeBytes), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AudioAttachmentContent(
    clientMsgId: String,
    attachment: MessageAttachment,
    playbackState: PlaybackState,
    onTogglePlayback: (sourceId: String, localPath: String?, remoteUrl: String?) -> Unit,
) {
    val isPlayingThis = playbackState is PlaybackState.Playing && playbackState.sourceId == clientMsgId
    val remoteUrl = attachment.id?.let { AttachmentUrls.content(it) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onTogglePlayback(clientMsgId, attachment.localContentUri, remoteUrl) },
            enabled = attachment.localContentUri != null || remoteUrl != null,
        ) {
            Icon(
                imageVector = if (isPlayingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlayingThis) R.string.chat_pause_voice else R.string.chat_play_voice,
                ),
            )
        }
        Text(formatDuration(attachment.durationMs ?: 0L), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImageViewerDialog(attachment: MessageAttachment, onDismiss: () -> Unit) {
    val model = attachment.localContentUri
        ?: attachment.id?.let { AttachmentUrls.content(it) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.chat_view_image),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VideoAttachmentContent(attachment: MessageAttachment, onClick: () -> Unit) {
    // Thumbnail is always the client-extracted or server-generated JPEG —
    // never the raw video bytes (Coil can't decode video frames from a
    // stream). Tapping opens VideoPlayerDialog, which only supports
    // playback for the sender's own local copy for now — see that
    // composable's doc comment for why a received video falls back to a
    // still-image preview instead of in-app playback.
    val thumbnailModel = attachment.localThumbnailUri
        ?: attachment.id?.let { AttachmentUrls.thumbnail(it) }

    Box(modifier = Modifier.widthIn(max = 260.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = thumbnailModel,
            contentDescription = attachment.originalFilename,
            modifier = Modifier.fillMaxWidth(),
        )
        Icon(
            imageVector = Icons.Filled.PlayCircle,
            contentDescription = stringResource(R.string.chat_play_video),
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.Center).width(48.dp).height(48.dp),
        )
        attachment.durationMs?.let { duration ->
            Text(
                text = formatDuration(duration),
                color = MaterialTheme.colorScheme.surface,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
    }
}

@Composable
private fun VideoPlayerDialog(attachment: MessageAttachment, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (attachment.localContentUri != null) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    factory = { context ->
                        VideoView(context).apply {
                            setVideoURI(Uri.parse(attachment.localContentUri))
                            setOnPreparedListener { it.start() }
                        }
                    },
                )
            } else {
                // In-app playback for a received (not-yet-locally-cached)
                // video is deferred — VideoView has no built-in way to
                // attach the bearer token a streamed fetch would need
                // (MediaPlayer's Uri+headers overload exists for exactly
                // this — see VoicePlaybackController — but wiring that
                // through VideoView specifically is left for a follow-up).
                // A larger still preview is honest and still useful,
                // rather than a playback control that would just fail.
                val model = attachment.localThumbnailUri
                    ?: attachment.id?.let { AttachmentUrls.thumbnail(it) }
                AsyncImage(
                    model = model,
                    contentDescription = attachment.originalFilename,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun stateLabelRes(state: DomainMessageState): Int = when (state) {
    DomainMessageState.PENDING, DomainMessageState.SENDING -> R.string.chat_message_state_sending
    DomainMessageState.SENT -> R.string.chat_message_state_sent
    DomainMessageState.READ -> R.string.chat_message_state_read
    DomainMessageState.FAILED -> R.string.chat_message_state_failed
}

@Composable
private fun Composer(
    isRecording: Boolean,
    editingMessage: Message?,
    onSend: (String) -> Unit,
    onSendAttachment: (contentUri: String, mimeType: String, originalFilename: String) -> Unit,
    onStartRecording: () -> Boolean,
    onStopRecordingAndSend: () -> Unit,
    onCancelRecording: () -> Unit,
    onCancelEdit: () -> Unit,
    onSubmitEdit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var permissionJustDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Prefills the composer with the message's current body the instant
    // edit mode is entered, and clears it again on cancel/save (both set
    // editingMessage back to null) — a plain `remember { mutableStateOf }`
    // wouldn't react to editingMessage changing after the first
    // composition, which is why this needs to be an effect instead.
    LaunchedEffect(editingMessage?.clientMsgId) {
        text = editingMessage?.body ?: ""
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val meta = resolveContentUriMeta(context, uri)
            onSendAttachment(uri.toString(), meta.mimeType, meta.displayName)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onStartRecording()
        } else {
            permissionJustDenied = true
        }
    }

    fun onMicClick() {
        if (isRecording) {
            onStopRecordingAndSend()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onStartRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column {
        if (permissionJustDenied) {
            Text(
                text = stringResource(R.string.chat_mic_permission_denied),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (editingMessage != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.chat_editing_message), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onCancelEdit) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel_edit))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { pickLauncher.launch("*/*") },
                enabled = !isRecording && editingMessage == null,
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.chat_attach))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                enabled = !isRecording,
                placeholder = { Text(stringResource(R.string.chat_composer_hint)) },
            )
            IconButton(onClick = ::onMicClick, enabled = editingMessage == null) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = stringResource(
                        if (isRecording) R.string.chat_stop_recording else R.string.chat_record_voice,
                    ),
                )
            }
            IconButton(
                enabled = !isRecording,
                onClick = {
                    if (text.isNotBlank()) {
                        if (editingMessage != null) {
                            onSubmitEdit(text)
                        } else {
                            onSend(text)
                        }
                        text = ""
                    }
                },
            ) {
                Icon(
                    imageVector = if (editingMessage != null) Icons.Filled.Check else Icons.Filled.Send,
                    contentDescription = stringResource(
                        if (editingMessage != null) R.string.chat_save_edit else R.string.chat_send,
                    ),
                )
            }
        }
    }
}
