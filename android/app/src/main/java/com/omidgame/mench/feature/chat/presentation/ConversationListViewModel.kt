package com.omidgame.mench.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.chat.domain.ChatRepository
import android.content.Context
import com.omidgame.mench.feature.chat.domain.ChatResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val realtimeServiceLauncher: com.omidgame.mench.core.notifications.RealtimeServiceLauncher,
    private val shortcutPublisher: com.omidgame.mench.core.notifications.ShortcutPublisher,
    private val productRepository: com.omidgame.mench.feature.product.ProductRepository,
    private val ecosystemRepository: com.omidgame.mench.feature.product.EcosystemRepository,
    private val profileRepository: com.omidgame.mench.feature.settings.domain.ProfileRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // Phase 2A: folders / active filter / manual-unread flags (local to this phone).
    private val _folders = MutableStateFlow(ChatListPrefs.folders(appContext))
    private val _filter = MutableStateFlow<ChatFilter>(ChatFilter.All)
    private val _manualUnread = MutableStateFlow(ChatListPrefs.manualUnread(appContext))

    private val _isRefreshing = MutableStateFlow(false)

    private val _showArchived = MutableStateFlow(false)

    private val _unseenStories = MutableStateFlow<Set<String>>(emptySet())

    /** Phase 3B. Conversations with stories you have not seen — they get a ring around their avatar in the list. */
    val unseenStoryConversations: StateFlow<Set<String>> = _unseenStories

    private fun refreshUnseenStories() {
        viewModelScope.launch {
            val result = chatRepository.unseenStoryConversations()
            if (result is ChatResult.Success) _unseenStories.value = result.value
        }
    }

    init {
        refreshUnseenStories()
        viewModelScope.launch { chatRepository.observeStoryChanges().collect { refreshUnseenStories() } }
    }

    private val listInputs = combine(
        chatRepository.observeConversations(),
        _isRefreshing,
        _showArchived,
        chatRepository.observeConversationPreviews(),
        chatRepository.observeTypingConversationIds(),
    ) { conversations, refreshing, showArchived, previews, typing ->
        ListInputs(conversations, refreshing, showArchived, previews, typing)
    }

    private data class ListInputs(
        val conversations: List<com.omidgame.mench.feature.chat.domain.Conversation>,
        val refreshing: Boolean,
        val showArchived: Boolean,
        val previews: Map<String, com.omidgame.mench.feature.chat.domain.ConversationPreview>,
        val typing: Set<String>,
    )

    val uiState: StateFlow<ConversationListUiState> = combine(
        listInputs,
        _folders,
        _filter,
        _manualUnread,
    ) { inputs, folders, filter, manualUnread ->
        val conversations = inputs.conversations
        when {
            inputs.refreshing && conversations.isEmpty() -> ConversationListUiState.Loading
            conversations.isEmpty() -> ConversationListUiState.Empty
            else -> {
                val activeFilter = ChatFolders.sanitize(filter, folders)
                fun isUnread(id: String) = id in manualUnread || (inputs.previews[id]?.unreadCount ?: 0) > 0
                val inView = conversations.filter { it.isArchived == inputs.showArchived }
                ConversationListUiState.Content(
                    conversations = inView.filter {
                        ChatFolders.matches(activeFilter, folders, it.id, it.isGroup, isUnread(it.id))
                    },
                    archivedCount = conversations.count { it.isArchived },
                    showingArchived = inputs.showArchived,
                    previews = inputs.previews,
                    typingIds = inputs.typing,
                    folders = folders,
                    filter = activeFilter,
                    manualUnread = manualUnread,
                    unreadChatCount = inView.count { isUnread(it.id) },
                    hasGroups = inView.any { it.isGroup },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConversationListUiState.Loading)

    init {
        // Connection lifetime is tied to "signed in", not to this screen —
        // disconnecting in onCleared() here would have dropped the socket
        // every time the user merely navigated from the conversation list
        // to an open chat (a different screen, a different ViewModel
        // instance, but the same underlying singleton connection). There
        // is no sign-out UI yet in this phase to hook a disconnect into;
        // when one exists, that's where disconnectRealtime() belongs, not
        // here.
        chatRepository.connectRealtime()
        realtimeServiceLauncher.start()
        refresh()
        // Phase 1D.1: remember this account's name/username for the account switcher.
        viewModelScope.launch {
            profileRepository.getProfile()?.let {
                com.omidgame.mench.core.accounts.AccountRegistry.updateActive(appContext, it.displayName, it.username, it.id.ifBlank { null })
            }
        }
        // Phase 1C: keep launcher shortcuts / Direct Share targets in sync with the top chats.
        viewModelScope.launch {
            chatRepository.observeConversations().collect { shortcutPublisher.publish(it) }
        }
    }

    /** Everything this phone has for the conversation, oldest first (used by the export action). */
    suspend fun messagesForExport(conversationId: String): List<com.omidgame.mench.feature.chat.domain.Message> =
        chatRepository.observeMessages(conversationId).first()

    /** Phase 1C: blocks the other person of a direct chat (server enforces it on send). */
    fun onBlockUser(userId: String) {
        viewModelScope.launch { productRepository.block(userId) }
    }

    /** Phase 1D.8: reports the other person of a direct chat to moderation. */
    fun onReportUser(userId: String, category: String, details: String?) {
        viewModelScope.launch { ecosystemRepository.report(null, userId, category, details) }
    }

    // ---- Phase 2A: join a group with an invite code ----
    private val _joinState = MutableStateFlow<JoinState>(JoinState.Idle)
    val joinState: StateFlow<JoinState> = _joinState

    sealed interface JoinState {
        data object Idle : JoinState
        data object Working : JoinState
        data class Preview(val code: String, val title: String?, val memberCount: Int) : JoinState
        data class Joined(val conversationId: String) : JoinState
        /** [reasonRes] is a string resource explaining why the code can't be used. */
        data class Failed(val reasonRes: Int) : JoinState
    }

    fun onLookupInvite(pasted: String) {
        val code = com.omidgame.mench.feature.chat.domain.GroupRules.extractInviteCode(pasted)
        if (code == null) {
            _joinState.value = JoinState.Failed(com.omidgame.mench.R.string.join_code_invalid)
            return
        }
        _joinState.value = JoinState.Working
        viewModelScope.launch {
            when (val result = chatRepository.previewInvite(code)) {
                is com.omidgame.mench.feature.chat.domain.ChatResult.Success -> {
                    val p = result.value
                    _joinState.value = when {
                        p.valid -> JoinState.Preview(code, p.title, p.memberCount)
                        p.problem == "expired" -> JoinState.Failed(com.omidgame.mench.R.string.join_code_expired)
                        p.problem == "used_up" -> JoinState.Failed(com.omidgame.mench.R.string.join_code_used_up)
                        p.problem == "revoked" -> JoinState.Failed(com.omidgame.mench.R.string.join_code_revoked)
                        else -> JoinState.Failed(com.omidgame.mench.R.string.join_code_invalid)
                    }
                }
                is com.omidgame.mench.feature.chat.domain.ChatResult.Failure ->
                    _joinState.value = JoinState.Failed(com.omidgame.mench.R.string.join_failed_network)
            }
        }
    }

    fun onConfirmJoin() {
        val preview = _joinState.value as? JoinState.Preview ?: return
        _joinState.value = JoinState.Working
        viewModelScope.launch {
            when (val result = chatRepository.joinByInvite(preview.code)) {
                is com.omidgame.mench.feature.chat.domain.ChatResult.Success -> {
                    refresh()
                    _joinState.value = JoinState.Joined(result.value)
                }
                is com.omidgame.mench.feature.chat.domain.ChatResult.Failure ->
                    _joinState.value = JoinState.Failed(com.omidgame.mench.R.string.join_failed_network)
            }
        }
    }

    fun onJoinDismissed() {
        _joinState.value = JoinState.Idle
    }

    fun onSelectFilter(filter: ChatFilter) {
        _filter.value = filter
    }

    fun onCreateFolder(name: String, thenAddConversationId: String?) {
        val id = UUID.randomUUID().toString()
        var updated = ChatFolders.create(_folders.value, id, name)
        if (thenAddConversationId != null && updated.any { it.id == id }) {
            updated = ChatFolders.setMembership(updated, id, thenAddConversationId, true)
        }
        saveFolders(updated)
        if (updated.any { it.id == id }) _filter.value = ChatFilter.Folder(id)
    }

    fun onRenameFolder(folderId: String, name: String) = saveFolders(ChatFolders.rename(_folders.value, folderId, name))

    fun onDeleteFolder(folderId: String) = saveFolders(ChatFolders.delete(_folders.value, folderId))

    fun onSetFolderMembership(folderId: String, conversationId: String, member: Boolean) =
        saveFolders(ChatFolders.setMembership(_folders.value, folderId, conversationId, member))

    private fun saveFolders(folders: List<ChatFolder>) {
        _folders.value = folders
        ChatListPrefs.saveFolders(appContext, folders)
    }

    /** Toggles the manual "unread" dot. Only meaningful for a chat that has no real unread messages. */
    fun onMarkUnread(conversationId: String, unread: Boolean) {
        val updated = if (unread) _manualUnread.value + conversationId else _manualUnread.value - conversationId
        _manualUnread.value = updated
        ChatListPrefs.saveManualUnread(appContext, updated)
    }

    /** Clears both kinds of "unread": the manual flag and the real unread count (read cursor moves to the latest message). */
    fun onMarkRead(conversationId: String) {
        onMarkUnread(conversationId, false)
        viewModelScope.launch { chatRepository.markConversationRead(conversationId) }
    }

    /** Opening a chat clears the manual flag (the real count is cleared by the chat screen itself). */
    fun onConversationOpened(conversationId: String) {
        if (conversationId in _manualUnread.value) onMarkUnread(conversationId, false)
    }

    fun onToggleArchivedView() {
        _showArchived.value = !_showArchived.value
    }

    fun onSetArchived(conversationId: String, archived: Boolean) {
        viewModelScope.launch { chatRepository.setConversationArchived(conversationId, archived) }
    }

    fun onSetPinned(conversationId: String, pinned: Boolean) {
        viewModelScope.launch { chatRepository.setConversationPinned(conversationId, pinned) }
    }

    fun onSetMuted(conversationId: String, muted: Boolean) {
        viewModelScope.launch { chatRepository.setConversationMuted(conversationId, muted) }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            chatRepository.refreshConversations()
            _isRefreshing.value = false
        }
    }

    /** Opens (creating on first use) the Saved messages chat and hands its id to [navigate]. */
    fun onOpenSaved(navigate: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = chatRepository.openSavedMessages()) {
                is com.omidgame.mench.feature.chat.domain.ChatResult.Success -> navigate(r.value.id)
                is com.omidgame.mench.feature.chat.domain.ChatResult.Failure -> Unit
            }
        }
    }

    companion object {
        /** Pinned-chat limit (Phase 2A), same order of magnitude as Telegram's free tier. */
        const val MAX_PINNED = 5
    }
}
