package com.omidgame.mench.feature.chat.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.media.RecordingResult
import com.omidgame.mench.core.media.VideoMetadataExtractor
import com.omidgame.mench.core.media.VoicePlaybackController
import com.omidgame.mench.core.media.VoiceRecorder
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.chat.domain.ChatResult
import com.omidgame.mench.feature.chat.domain.Message
import com.omidgame.mench.feature.chat.domain.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private val VIDEO_MIME_TYPES = setOf("video/mp4", "video/webm", "video/3gpp")

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val voiceRecorder: VoiceRecorder,
    private val videoMetadataExtractor: VideoMetadataExtractor,
    val voicePlayback: VoicePlaybackController,
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _editingMessage = MutableStateFlow<Message?>(null)
    /**
     * Fetched once via listMembers rather than kept live — a member's
     * display name changing mid-conversation and this label going briefly
     * stale is a fine tradeoff against re-fetching the whole roster on
     * every recomposition. Harmless (and simply unused) for a direct
     * conversation — see ChatUiState.isGroupConversation, which is what
     * actually gates whether ChatScreen shows this label at all.
     */
    private val _memberNames = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.observeMessages(conversationId),
        chatRepository.observeTypingConversationIds(),
        _editingMessage,
        chatRepository.observeConversations(),
        _memberNames,
    ) { messages, typingIds, editingMessage, conversations, memberNames ->
        val thisConversation = conversations.firstOrNull { it.id == conversationId }
        ChatUiState(
            messages = messages,
            isOtherTyping = conversationId in typingIds,
            editingMessage = editingMessage,
            conversations = conversations,
            isGroupConversation = thisConversation?.isGroup == true,
            memberDisplayNames = memberNames,
            headerTitle = thisConversation?.let { it.title ?: it.otherUserDisplayName ?: it.otherUserPhoneE164 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    init {
        viewModelScope.launch {
            chatRepository.syncMessages(conversationId)
        }
        viewModelScope.launch {
            when (val result = chatRepository.listMembers(conversationId)) {
                is ChatResult.Success ->
                    _memberNames.value = result.value.associate { it.userId to (it.displayName ?: it.phoneE164) }
                is ChatResult.Failure -> Unit // best-effort — sender labels just stay blank
            }
        }
    }

    fun onSendMessage(body: String) {
        viewModelScope.launch {
            sendMessageUseCase(conversationId, body)
        }
    }

    /**
     * Handles any picked content — image, generic file, or video — from
     * the composer's single attach button. Video is detected by mime type
     * and routed through metadata extraction first; everything else goes
     * straight through. One button, no separate video-specific UI entry
     * point needed.
     */
    fun onSendAttachment(contentUri: String, mimeType: String, originalFilename: String) {
        viewModelScope.launch {
            if (mimeType in VIDEO_MIME_TYPES) {
                val metadata = withContext(Dispatchers.IO) { videoMetadataExtractor.extract(contentUri) }
                chatRepository.sendAttachment(
                    conversationId,
                    contentUri,
                    mimeType,
                    originalFilename,
                    caption = null,
                    durationMs = metadata.durationMs,
                    thumbnailLocalUri = metadata.thumbnailLocalPath,
                    widthPx = metadata.widthPx,
                    heightPx = metadata.heightPx,
                )
            } else {
                chatRepository.sendAttachment(conversationId, contentUri, mimeType, originalFilename, caption = null)
            }
        }
    }

    /** Returns false if recording could not start (e.g. mic unavailable) — the caller (already past the permission check) should surface that. */
    fun onStartRecording(): Boolean {
        val started = voiceRecorder.start()
        _isRecording.value = started
        return started
    }

    fun onStopRecordingAndSend() {
        _isRecording.value = false
        when (val result = voiceRecorder.stop()) {
            is RecordingResult.Success -> {
                // The file already lives in app-private storage (VoiceRecorder
                // wrote it there directly) — routed through the same
                // sendAttachment()/AttachmentCache path as a picked file for
                // consistency, at the cost of one redundant copy. Not worth a
                // second code path for.
                val fileUri = Uri.fromFile(File(result.filePath)).toString()
                viewModelScope.launch {
                    chatRepository.sendAttachment(
                        conversationId,
                        fileUri,
                        result.mimeType,
                        originalFilename = "voice-message.m4a",
                        caption = null,
                        durationMs = result.durationMs,
                    )
                }
            }
            RecordingResult.Failed -> Unit // too short or failed to finalize — nothing to send
        }
    }

    fun onCancelRecording() {
        _isRecording.value = false
        voiceRecorder.cancel()
    }

    fun onToggleVoicePlayback(sourceId: String, localPath: String?, remoteUrl: String?) {
        voicePlayback.togglePlay(sourceId, localPath, remoteUrl)
    }

    /** Called when the user has actually seen the latest messages (e.g. the list is at the bottom), not merely opened the screen. */
    fun onMessagesRead(upToSequence: Long) {
        viewModelScope.launch {
            chatRepository.markRead(conversationId, upToSequence)
        }
    }

    /** Puts the composer into edit mode for this message — ChatScreen prefills the text field and swaps Send for Save. Only ever called for a message the UI already restricted to isOwn && kind==TEXT && serverId != null && not deleted (see MessageBubble's long-press menu). */
    fun onStartEdit(message: Message) {
        _editingMessage.value = message
    }

    fun onCancelEdit() {
        _editingMessage.value = null
    }

    fun onSubmitEdit(newBody: String) {
        val target = _editingMessage.value ?: return
        val serverId = target.serverId ?: return
        val trimmed = newBody.trim()
        if (trimmed.isEmpty()) return
        _editingMessage.value = null
        viewModelScope.launch {
            chatRepository.editMessage(conversationId, serverId, trimmed)
        }
    }

    fun onDeleteMessage(message: Message) {
        val serverId = message.serverId ?: return
        viewModelScope.launch {
            chatRepository.deleteMessage(conversationId, serverId)
        }
    }

    /** Tapping an emoji the user has already reacted with clears it — a toggle, not a second reaction. */
    fun onReact(message: Message, emoji: String) {
        val serverId = message.serverId ?: return
        val alreadyReactedWithThis = message.reactions.any { it.emoji == emoji && it.reactedByMe }
        viewModelScope.launch {
            if (alreadyReactedWithThis) {
                chatRepository.clearReaction(conversationId, serverId)
            } else {
                chatRepository.setReaction(conversationId, serverId, emoji)
            }
        }
    }

    fun onForward(message: Message, targetConversationId: String) {
        val serverId = message.serverId ?: return
        viewModelScope.launch {
            chatRepository.forwardMessage(conversationId, serverId, targetConversationId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voicePlayback.stop()
    }
}
