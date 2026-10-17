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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
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

    /** Phase 2A: live elapsed time + levels while recording (drives the composer's recording bar). */
    val recorderLive: StateFlow<com.omidgame.mench.core.media.RecorderLive> = voiceRecorder.live

    /** Phase 2A: playhead, speed and seek for voice bubbles, handed to ChatScreen as one object. */
    val voiceUi = com.omidgame.mench.feature.chat.presentation.VoiceUi(
        progress = voicePlayback.progress,
        speed = voicePlayback.speed,
        onSeek = { id, local, remote, fraction -> voicePlayback.playFrom(id, local, remote, fraction) },
        onCycleSpeed = { voicePlayback.cycleSpeed() },
    )

    // ---- Phase 2A: polls (fetched live; the message itself is the marker "poll:v1:<id>") ----
    private val _polls = MutableStateFlow<Map<String, com.omidgame.mench.feature.chat.domain.Poll>>(emptyMap())
    private val requestedPolls = HashSet<String>()
    private val _pollErrors = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 2)

    private val _selfUserId = MutableStateFlow<String?>(null)
    /** Phase 2A: the signed-in user's id, so the "who reacted" sheet can label the current user. */
    val selfUserId: StateFlow<String?> = _selfUserId

    val pollUi = PollUi(
        polls = _polls,
        selfUserId = _selfUserId,
        errors = _pollErrors,
        onLoad = ::loadPoll,
        onVote = ::votePoll,
        onClose = ::closePoll,
    )

    init {
        refreshScheduled()
        refreshGroupInfo() // a 4xx for a direct chat simply leaves it null
        viewModelScope.launch { _selfUserId.value = chatRepository.currentUserId() }
        viewModelScope.launch {
            chatRepository.observePollUpdates().collect { poll ->
                if (poll.conversationId == conversationId) _polls.value = _polls.value + (poll.id to poll)
            }
        }
    }

    private fun loadPoll(pollId: String) {
        if (_polls.value.containsKey(pollId) || !requestedPolls.add(pollId)) return
        viewModelScope.launch {
            when (val result = chatRepository.loadPoll(pollId)) {
                is com.omidgame.mench.feature.chat.domain.ChatResult.Success -> _polls.value = _polls.value + (pollId to result.value)
                // Allow a later bubble composition to retry (e.g. the phone was offline the first time).
                is com.omidgame.mench.feature.chat.domain.ChatResult.Failure -> requestedPolls.remove(pollId)
            }
        }
    }

    private fun votePoll(poll: com.omidgame.mench.feature.chat.domain.Poll, optionIds: List<String>) {
        val self = _selfUserId.value ?: return
        // Optimistic: the bars move at once; the server's answer (or a reload on failure) replaces this.
        _polls.value = _polls.value + (poll.id to poll.withMyVotes(self, optionIds))
        viewModelScope.launch {
            when (val result = chatRepository.votePoll(poll.id, optionIds)) {
                is com.omidgame.mench.feature.chat.domain.ChatResult.Success -> _polls.value = _polls.value + (poll.id to result.value)
                is com.omidgame.mench.feature.chat.domain.ChatResult.Failure -> {
                    _pollErrors.tryEmit(com.omidgame.mench.R.string.poll_vote_failed)
                    (chatRepository.loadPoll(poll.id) as? com.omidgame.mench.feature.chat.domain.ChatResult.Success)
                        ?.let { _polls.value = _polls.value + (poll.id to it.value) }
                        ?: run { _polls.value = _polls.value + (poll.id to poll) }
                }
            }
        }
    }

    private fun closePoll(poll: com.omidgame.mench.feature.chat.domain.Poll) {
        viewModelScope.launch {
            when (val result = chatRepository.closePoll(poll.id)) {
                is com.omidgame.mench.feature.chat.domain.ChatResult.Success -> _polls.value = _polls.value + (poll.id to result.value)
                is com.omidgame.mench.feature.chat.domain.ChatResult.Failure -> _pollErrors.tryEmit(com.omidgame.mench.R.string.poll_close_failed)
            }
        }
    }

    /** Creates the poll on the server, then sends the chat message that displays it through the normal (outbox) send path. */
    fun onCreatePoll(question: String, options: List<String>, multiple: Boolean) {
        viewModelScope.launch {
            when (val result = chatRepository.createPoll(conversationId, question, options, multiple)) {
                is com.omidgame.mench.feature.chat.domain.ChatResult.Success -> {
                    val poll = result.value
                    _polls.value = _polls.value + (poll.id to poll)
                    RichBody.encodePoll(poll.id)?.let { sendMessageUseCase(conversationId, it, null) }
                }
                is com.omidgame.mench.feature.chat.domain.ChatResult.Failure -> _pollErrors.tryEmit(com.omidgame.mench.R.string.poll_create_failed)
            }
        }
    }

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
    private val _memberAvatars = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** How many of the newest messages are loaded. Grows while scrolling up; search loads everything. */
    private val windowLimit = MutableStateFlow(INITIAL_WINDOW)

    /** Loads another page of older messages (no-op once everything is loaded). */
    fun loadOlderMessages() { windowLimit.update { if (it >= Int.MAX_VALUE - WINDOW_STEP) it else it + WINDOW_STEP } }

    /** In-chat search needs the whole history. */
    fun loadAllMessages() { windowLimit.value = Int.MAX_VALUE }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messageWindow = windowLimit.flatMapLatest { limit ->
        chatRepository.observeMessageWindow(conversationId, limit).map { list -> list to (list.size >= limit) }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        messageWindow,
        chatRepository.observeTypingConversationIds(),
        _editingMessage,
        chatRepository.observeConversations(),
        _memberNames,
        _memberAvatars,
    ) { windowed, typingIds, editingMessage, conversations, memberNames, memberAvatars ->
        val messages = windowed.first
        val thisConversation = conversations.firstOrNull { it.id == conversationId }
        ChatUiState(
            messages = messages,
            hasOlderMessages = windowed.second,
            conversationId = conversationId,
            isOtherTyping = conversationId in typingIds,
            editingMessage = editingMessage,
            conversations = conversations,
            isGroupConversation = thisConversation?.isGroup == true,
            isSavedMessages = thisConversation?.isSaved == true,
            memberDisplayNames = memberNames,
            memberAvatarVersions = memberAvatars,
            togetherSince = thisConversation?.togetherSince,
            nextMeetDate = thisConversation?.nextMeetDate,
            otherUserId = thisConversation?.takeIf { !it.isGroup }?.otherUserId,
            otherUserAvatarVersion = thisConversation?.otherUserAvatarVersion,
            pinnedMessage = thisConversation?.pinnedMessageId?.let { id -> messages.firstOrNull { it.serverId == id } },
            headerTitle = thisConversation?.let { it.title ?: it.otherUserDisplayName ?: it.otherUserPhoneE164 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    /**
     * Phase 8. Presence of the other person in a direct chat (null for
     * groups / unknown). Standalone for the same fixed-arity-combine reason
     * as isRecording below.
     */
    val otherPresence: StateFlow<com.omidgame.mench.feature.chat.domain.UserPresence?> = combine(
        chatRepository.observePresence(),
        chatRepository.observeConversations(),
    ) { presenceByUserId, conversations ->
        conversations.firstOrNull { it.id == conversationId }
            ?.takeIf { !it.isGroup }
            ?.otherUserId
            ?.let { presenceByUserId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Phase 9. Emits each time the partner taps "I miss you" in THIS conversation (drives the heart animation + vibration). */
    val missYouEvents: kotlinx.coroutines.flow.Flow<Unit> = chatRepository.observeMissYou()
        .filter { it == conversationId }
        .map { }

    /** Phase 3A. Emits the held duration (ms) each time the partner sends a live heartbeat in THIS conversation. */
    val heartbeatEvents: kotlinx.coroutines.flow.Flow<Long> = chatRepository.observeHeartbeats()
        .filter { it.first == conversationId }
        .map { it.second }

    /** Phase 3A. Emits the effect wire name each time another member sends a hand-picked effect in THIS conversation. */
    val togetherEvents: kotlinx.coroutines.flow.Flow<com.omidgame.mench.feature.chat.domain.TogetherSignal> = chatRepository.observeTogether()
        .filter { it.first == conversationId }
        .map { it.second }

    suspend fun setSecureChat(enabled: Boolean): Boolean =
        chatRepository.setSecureChat(conversationId, uiState.value.otherUserId, enabled)

    fun onSendTogether(kind: String, attachmentId: String?, positionMs: Long, playing: Boolean) {
        viewModelScope.launch { chatRepository.sendTogether(conversationId, kind, attachmentId, positionMs, playing) }
    }

    val screenEffectEvents: kotlinx.coroutines.flow.Flow<String> = chatRepository.observeScreenEffects()
        .filter { it.first == conversationId }
        .map { it.second }

    private val _letters = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.Letter>>(emptyList())

    /** Phase 9. Letters of this direct conversation, newest first; loaded on demand and whenever one is received/opened. */
    val letters: StateFlow<List<com.omidgame.mench.feature.chat.domain.Letter>> = _letters

    init {
        viewModelScope.launch {
            chatRepository.observeLetterChanges().filter { it == conversationId }.collect { refreshLetters() }
        }
    }

    private val _moods = MutableStateFlow<com.omidgame.mench.feature.chat.domain.Moods?>(null)

    /** Phase 9. Both people's moods (the UI only shows ones set today). */
    val moods: StateFlow<com.omidgame.mench.feature.chat.domain.Moods?> = _moods

    init {
        refreshMoods()
        viewModelScope.launch {
            chatRepository.observeMoodChanges().filter { it == conversationId }.collect { refreshMoods() }
        }
    }

    fun refreshMoods() {
        viewModelScope.launch {
            val result = chatRepository.getMoods(conversationId)
            if (result is ChatResult.Success) _moods.value = result.value
        }
    }

    fun onSetMood(emoji: String, text: String?) {
        viewModelScope.launch {
            val result = chatRepository.setMood(conversationId, emoji, text)
            if (result is ChatResult.Success) refreshMoods()
        }
    }

    suspend fun loadLinkPreview(url: String): com.omidgame.mench.feature.chat.domain.LinkPreview? = chatRepository.linkPreview(url)

    fun onPinMessage(message: Message) {
        val serverId = message.serverId ?: return
        viewModelScope.launch { chatRepository.setPinnedMessage(conversationId, serverId) }
    }

    fun onUnpinMessage() {
        viewModelScope.launch { chatRepository.setPinnedMessage(conversationId, null) }
    }

    private val _question = MutableStateFlow<com.omidgame.mench.feature.chat.domain.Question?>(null)
    val question: StateFlow<com.omidgame.mench.feature.chat.domain.Question?> = _question

    private val _wishes = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.Wish>>(emptyList())
    val wishes: StateFlow<List<com.omidgame.mench.feature.chat.domain.Wish>> = _wishes

    init {
        viewModelScope.launch {
            chatRepository.observeQuestionChanges().filter { it == conversationId }.collect { refreshQuestion() }
        }
        viewModelScope.launch {
            chatRepository.observeWishlistChanges().filter { it == conversationId }.collect { refreshWishes() }
        }
    }

    fun refreshQuestion() {
        viewModelScope.launch {
            val result = chatRepository.getQuestion(conversationId)
            if (result is ChatResult.Success) _question.value = result.value
        }
    }

    fun onAnswerQuestion(answer: String) {
        viewModelScope.launch {
            val result = chatRepository.answerQuestion(conversationId, answer)
            if (result is ChatResult.Success) refreshQuestion()
        }
    }

    fun refreshWishes() {
        viewModelScope.launch {
            val result = chatRepository.listWishes(conversationId)
            if (result is ChatResult.Success) _wishes.value = result.value
        }
    }

    fun onAddWish(title: String) {
        viewModelScope.launch {
            val result = chatRepository.createWish(conversationId, title)
            if (result is ChatResult.Success) refreshWishes()
        }
    }

    fun onToggleWish(wish: com.omidgame.mench.feature.chat.domain.Wish) {
        viewModelScope.launch {
            val result = chatRepository.setWishDone(conversationId, wish.id, !wish.done)
            if (result is ChatResult.Success) refreshWishes()
        }
    }

    fun onDeleteWish(wishId: String) {
        viewModelScope.launch {
            val result = chatRepository.deleteWish(conversationId, wishId)
            if (result is ChatResult.Success) _wishes.value = _wishes.value.filterNot { it.id == wishId }
        }
    }

    private val _album = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.AlbumItem>>(emptyList())

    /** Phase 3. Photos of the shared album, newest first. */
    val albumItems: StateFlow<List<com.omidgame.mench.feature.chat.domain.AlbumItem>> = _album

    fun refreshAlbum() {
        viewModelScope.launch {
            val result = chatRepository.listAlbum(conversationId)
            if (result is ChatResult.Success) _album.value = result.value
        }
    }

    fun onAddAlbumPhoto(photo: com.omidgame.mench.feature.chat.domain.PickedPhoto, caption: String?) {
        viewModelScope.launch {
            val result = chatRepository.addAlbumPhoto(conversationId, photo, caption)
            if (result is ChatResult.Success) refreshAlbum()
        }
    }

    fun onReactAlbumItem(itemId: String, emoji: String?) {
        // Optimistic: show your reaction right away, the server confirms (and the partner's phone updates) in the background.
        _album.value = _album.value.map { if (it.id == itemId) it.copy(myReaction = emoji) else it }
        viewModelScope.launch { chatRepository.reactAlbumItem(conversationId, itemId, emoji) }
    }

    fun onDeleteAlbumItem(itemId: String) {
        viewModelScope.launch {
            val result = chatRepository.deleteAlbumItem(conversationId, itemId)
            if (result is ChatResult.Success) _album.value = _album.value.filterNot { it.id == itemId }
        }
    }

    private val _yearMessages = MutableStateFlow<com.omidgame.mench.feature.chat.domain.YearMessageStats?>(null)

    /** Phase 3A. Statistics of this year's messages, or null until loaded. */
    val yearMessageStats: StateFlow<com.omidgame.mench.feature.chat.domain.YearMessageStats?> = _yearMessages

    fun loadYearMessageStats() {
        viewModelScope.launch {
            _yearMessages.value = chatRepository.loadYearMessageStats(conversationId, java.time.LocalDate.now().year)
        }
    }

    private val _quizzes = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.QuizSummary>>(emptyList())

    /** Phase 3A. Quizzes of this conversation, newest first. */
    val quizzes: StateFlow<List<com.omidgame.mench.feature.chat.domain.QuizSummary>> = _quizzes

    fun refreshQuizzes() {
        viewModelScope.launch {
            val result = chatRepository.listQuizzes(conversationId)
            if (result is ChatResult.Success) _quizzes.value = result.value
        }
    }

    fun onCreateQuiz(title: String, questions: List<com.omidgame.mench.feature.chat.domain.QuizQuestion>) {
        viewModelScope.launch {
            val result = chatRepository.createQuiz(conversationId, title, questions)
            if (result is ChatResult.Success) refreshQuizzes()
        }
    }

    /** Loads one quiz; [onResult] gets null on failure. */
    fun onLoadQuiz(quizId: String, onResult: (com.omidgame.mench.feature.chat.domain.QuizDetail?) -> Unit) {
        viewModelScope.launch {
            val result = chatRepository.getQuiz(conversationId, quizId)
            onResult((result as? ChatResult.Success)?.value)
        }
    }

    /** Submits the answers (one per question); [onResult] gets null on failure (for instance when already answered). */
    fun onSubmitQuiz(quizId: String, answers: List<Int>, onResult: (com.omidgame.mench.feature.chat.domain.QuizResult?) -> Unit) {
        viewModelScope.launch {
            val result = chatRepository.answerQuiz(conversationId, quizId, answers)
            if (result is ChatResult.Success) refreshQuizzes()
            onResult((result as? ChatResult.Success)?.value)
        }
    }

    fun onDeleteQuiz(quizId: String) {
        viewModelScope.launch {
            val result = chatRepository.deleteQuiz(conversationId, quizId)
            if (result is ChatResult.Success) _quizzes.value = _quizzes.value.filterNot { it.id == quizId }
        }
    }

    private val _vault = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.VaultItem>>(emptyList())

    /** Phase 3. Encrypted vault notes (opaque blobs, decrypted only in the dialog after unlocking). */
    val vaultItems: StateFlow<List<com.omidgame.mench.feature.chat.domain.VaultItem>> = _vault

    fun refreshVault() {
        viewModelScope.launch {
            val result = chatRepository.listVault(conversationId)
            if (result is ChatResult.Success) _vault.value = result.value
        }
    }

    fun onAddVaultItem(blob: String) {
        viewModelScope.launch {
            val result = chatRepository.createVaultItem(conversationId, blob)
            if (result is ChatResult.Success) refreshVault()
        }
    }

    fun onDeleteVaultItem(itemId: String) {
        viewModelScope.launch {
            val result = chatRepository.deleteVaultItem(conversationId, itemId)
            if (result is ChatResult.Success) _vault.value = _vault.value.filterNot { it.id == itemId }
        }
    }

    private val _stories = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.Story>>(emptyList())

    /** Phase 3B. Active stories of this conversation, oldest first. */
    val stories: StateFlow<List<com.omidgame.mench.feature.chat.domain.Story>> = _stories

    fun refreshStories() {
        viewModelScope.launch {
            val result = chatRepository.listStories(conversationId)
            if (result is ChatResult.Success) _stories.value = result.value
        }
    }

    fun onCreateStory(body: String, emoji: String?, palette: Int, photo: com.omidgame.mench.feature.chat.domain.PickedPhoto?) {
        viewModelScope.launch {
            val result = chatRepository.createStory(conversationId, body, emoji, palette, photo)
            if (result is ChatResult.Success) refreshStories()
        }
    }

    fun onViewStory(storyId: String) {
        // Optimistic: mark seen locally right away, tell the server in the background.
        _stories.value = _stories.value.map { if (it.id == storyId) it.copy(seen = true) else it }
        viewModelScope.launch { chatRepository.viewStory(conversationId, storyId) }
    }

    fun onDeleteStory(storyId: String) {
        viewModelScope.launch {
            val result = chatRepository.deleteStory(conversationId, storyId)
            if (result is ChatResult.Success) _stories.value = _stories.value.filterNot { it.id == storyId }
        }
    }

    private val _canvas = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.CanvasStroke>>(emptyList())

    /** Phase 3A. Strokes on the shared canvas (own strokes appear immediately, the partner's on the next refresh). */
    val canvasStrokes: StateFlow<List<com.omidgame.mench.feature.chat.domain.CanvasStroke>> = _canvas

    fun refreshCanvas() {
        viewModelScope.launch {
            val result = chatRepository.getCanvas(conversationId)
            if (result is ChatResult.Success) _canvas.value = result.value
        }
    }

    fun onAppendStrokes(strokes: List<com.omidgame.mench.feature.chat.domain.CanvasStroke>) {
        if (strokes.isEmpty()) return
        _canvas.value = _canvas.value + strokes
        viewModelScope.launch {
            val result = chatRepository.appendCanvasStrokes(conversationId, strokes)
            // On failure (full canvas, offline) fall back to the server's truth.
            if (result !is ChatResult.Success) refreshCanvas()
        }
    }

    fun onClearCanvas() {
        _canvas.value = emptyList()
        viewModelScope.launch { chatRepository.clearCanvas(conversationId) }
    }

    private val _counters = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.Counter>>(emptyList())

    /** Phase 3A. Shared counters of this direct conversation. */
    val counters: StateFlow<List<com.omidgame.mench.feature.chat.domain.Counter>> = _counters

    fun refreshCounters() {
        viewModelScope.launch {
            val result = chatRepository.listCounters(conversationId)
            if (result is ChatResult.Success) _counters.value = result.value
        }
    }

    fun onCreateCounter(title: String, emoji: String?, kind: String, anchorDateIso: String?, theme: Int) {
        viewModelScope.launch {
            val result = chatRepository.createCounter(conversationId, title, emoji, kind, anchorDateIso, theme)
            if (result is ChatResult.Success) refreshCounters()
        }
    }

    fun onBumpCounter(counterId: String, delta: Int) {
        viewModelScope.launch {
            val result = chatRepository.bumpCounter(conversationId, counterId, delta)
            if (result is ChatResult.Success) {
                val updated = result.value
                _counters.value = _counters.value.map { if (it.id == updated.id) updated else it }
            }
        }
    }

    fun onDeleteCounter(counterId: String) {
        viewModelScope.launch {
            val result = chatRepository.deleteCounter(conversationId, counterId)
            if (result is ChatResult.Success) _counters.value = _counters.value.filterNot { it.id == counterId }
        }
    }

    private val _memories = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.Memory>>(emptyList())

    /** Phase 9. Shared memories timeline of this direct conversation. */
    val memories: StateFlow<List<com.omidgame.mench.feature.chat.domain.Memory>> = _memories

    /** Phase 9. Emits "hug" or "kiss" each time the partner sends one in THIS conversation. */
    val affectionEvents: kotlinx.coroutines.flow.Flow<String> = chatRepository.observeAffection()
        .filter { it.first == conversationId }
        .map { it.second }

    init {
        viewModelScope.launch {
            chatRepository.observeMemoryChanges().filter { it == conversationId }.collect { refreshMemories() }
        }
        viewModelScope.launch {
            chatRepository.observeCounterChanges().filter { it == conversationId }.collect { refreshCounters() }
        }
        viewModelScope.launch {
            chatRepository.observeCanvasChanges().filter { it == conversationId }.collect { refreshCanvas() }
        }
        viewModelScope.launch {
            chatRepository.observeStoryChanges().filter { it == conversationId }.collect { refreshStories() }
        }
        viewModelScope.launch {
            chatRepository.observeVaultChanges().filter { it == conversationId }.collect { refreshVault() }
        }
        viewModelScope.launch {
            chatRepository.observeQuizChanges().filter { it == conversationId }.collect { refreshQuizzes() }
        }
        viewModelScope.launch {
            chatRepository.observeAlbumChanges().filter { it == conversationId }.collect { refreshAlbum() }
        }
    }

    fun refreshMemories() {
        viewModelScope.launch {
            val result = chatRepository.listMemories(conversationId)
            if (result is ChatResult.Success) _memories.value = result.value
        }
    }

    fun onCreateMemory(
        title: String,
        note: String?,
        memoryDateIso: String,
        photoUri: String?,
        photoMimeType: String?,
        photoFilename: String?,
    ) {
        viewModelScope.launch {
            val result = chatRepository.createMemory(
                conversationId, title, note, memoryDateIso, photoUri, photoMimeType, photoFilename,
            )
            if (result is ChatResult.Success) refreshMemories()
        }
    }

    fun onDeleteMemory(memoryId: String) {
        viewModelScope.launch {
            val result = chatRepository.deleteMemory(conversationId, memoryId)
            if (result is ChatResult.Success) _memories.value = _memories.value.filterNot { it.id == memoryId }
        }
    }

    fun onSendAffection(kind: String) {
        viewModelScope.launch { chatRepository.sendAffection(conversationId, kind) }
    }

    fun refreshLetters() {
        viewModelScope.launch {
            val result = chatRepository.listLetters(conversationId)
            if (result is ChatResult.Success) _letters.value = result.value
        }
    }

    fun onCreateLetter(title: String, body: String, unlockAtIso: String?, photo: com.omidgame.mench.feature.chat.domain.PickedPhoto?) {
        viewModelScope.launch {
            val result = chatRepository.createLetter(conversationId, title, body, unlockAtIso, photo)
            if (result is ChatResult.Success) refreshLetters()
        }
    }

    /** Opens a sealed letter (server enforces the unlock time) and swaps the opened copy into the list. */
    fun onOpenLetter(letterId: String) {
        viewModelScope.launch {
            val result = chatRepository.openLetter(conversationId, letterId)
            if (result is ChatResult.Success) {
                _letters.value = _letters.value.map { if (it.id == letterId) result.value else it }
            }
        }
    }

    fun onSendMissYou() {
        viewModelScope.launch { chatRepository.sendMissYou(conversationId) }
    }

    /** Phase 2A: translates a message into Persian, or into English when it is already mostly Arabic-script. Null = unavailable. */
    suspend fun translate(message: Message): String? {
        val text = message.body?.takeIf { it.isNotBlank() } ?: return null
        val arabicScript = text.count { it.code in 0x0600..0x06FF }
        val target = if (arabicScript * 2 > text.count { it.isLetter() }) "en" else "fa"
        return when (val r = chatRepository.translateText(text, target)) {
            is ChatResult.Success -> r.value
            is ChatResult.Failure -> null
        }
    }

    /** Phase 2A: who has read one of my messages in this group; empty list on any failure (the dialog shows an error line). */
    suspend fun loadSeenBy(message: Message): List<com.omidgame.mench.feature.chat.domain.SeenBy>? {
        val serverId = message.serverId ?: return null
        return when (val r = chatRepository.listSeenBy(conversationId, serverId)) {
            is ChatResult.Success -> r.value
            is ChatResult.Failure -> null
        }
    }

    /** Phase 2: earlier versions of an edited message; null on any failure (the dialog shows an error line). */
    suspend fun loadEditHistory(message: Message): List<com.omidgame.mench.feature.chat.domain.EditVersion>? {
        val serverId = message.serverId ?: return null
        return when (val r = chatRepository.listEditHistory(conversationId, serverId)) {
            is ChatResult.Success -> r.value
            is ChatResult.Failure -> null
        }
    }

    /** Sends a live heartbeat; [onResult] gets true when the partner was online to feel it. */
    fun onSendHeartbeat(durationMs: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = chatRepository.sendHeartbeat(conversationId, durationMs)
            onResult(if (result is ChatResult.Success) result.value else false)
        }
    }

    fun onSendScreenEffect(effectWire: String) {
        viewModelScope.launch { chatRepository.sendScreenEffect(conversationId, effectWire) }
    }

    fun onSetUsDates(togetherSince: String?, nextMeetDate: String?) {
        viewModelScope.launch { chatRepository.setUsDates(conversationId, togetherSince, nextMeetDate) }
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    /**
     * Deliberately kept out of the uiState combine() above rather than
     * added as a 6th flow — kotlinx.coroutines.flow.combine's fixed-arity
     * overloads stop at 5, and restructuring that combine into the
     * array-based overload for one more field isn't worth it when
     * ChatScreen already collects isRecording the same standalone way,
     * immediately below.
     */
    private val _replyingToMessage = MutableStateFlow<Message?>(null)
    val replyingToMessage: StateFlow<Message?> = _replyingToMessage

    init {
        viewModelScope.launch {
            chatRepository.syncMessages(conversationId)
        }
        viewModelScope.launch {
            when (val result = chatRepository.listMembers(conversationId)) {
                is ChatResult.Success ->
                {
                    _memberNames.value = result.value.associate { it.userId to (it.displayName ?: it.phoneE164) }
                    _memberAvatars.value = result.value.mapNotNull { m -> m.avatarVersion?.let { m.userId to it } }.toMap()
                }
                is ChatResult.Failure -> Unit // best-effort — sender labels just stay blank
            }
        }
    }

    fun onSendMessage(body: String) = onSendMessage(body, silent = false)

    /** [silent] = deliver without a push notification on the other phone (Phase 2A: long-press Send). */
    fun onSendMessage(body: String, silent: Boolean) {
        val replyToServerId = _replyingToMessage.value?.serverId
        viewModelScope.launch {
            sendMessageUseCase(conversationId, body, replyToServerId, silent)
        }
        _replyingToMessage.value = null
    }

    // ---- Phase 2A: channel mode (only admins post) ----
    private val _groupInfo = MutableStateFlow<com.omidgame.mench.feature.chat.domain.GroupInfo?>(null)
    /** Null for direct chats or before it loads; ChatScreen replaces the composer with a note when canPost is false. */
    val groupInfo: StateFlow<com.omidgame.mench.feature.chat.domain.GroupInfo?> = _groupInfo

    fun refreshGroupInfo() {
        viewModelScope.launch {
            (chatRepository.getGroupInfo(conversationId) as? com.omidgame.mench.feature.chat.domain.ChatResult.Success)
                ?.let { _groupInfo.value = it.value }
        }
    }

    // ---- Phase 2A: scheduled messages ----
    private val _scheduled = MutableStateFlow<List<com.omidgame.mench.feature.chat.domain.ScheduledMessage>>(emptyList())
    val scheduled: StateFlow<List<com.omidgame.mench.feature.chat.domain.ScheduledMessage>> = _scheduled
    private val _scheduleErrors = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 2)
    val scheduleErrors: kotlinx.coroutines.flow.SharedFlow<Int> = _scheduleErrors

    fun refreshScheduled() {
        viewModelScope.launch {
            (chatRepository.listScheduled(conversationId) as? com.omidgame.mench.feature.chat.domain.ChatResult.Success)
                ?.let { _scheduled.value = it.value }
        }
    }

    fun onScheduleMessage(body: String, sendAtEpochMillis: Long, silent: Boolean) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (chatRepository.scheduleMessage(conversationId, trimmed, sendAtEpochMillis, silent)) {
                is com.omidgame.mench.feature.chat.domain.ChatResult.Success -> refreshScheduled()
                is com.omidgame.mench.feature.chat.domain.ChatResult.Failure -> _scheduleErrors.tryEmit(com.omidgame.mench.R.string.schedule_failed)
            }
        }
    }

    fun onCancelScheduled(message: com.omidgame.mench.feature.chat.domain.ScheduledMessage) {
        viewModelScope.launch {
            chatRepository.cancelScheduled(message.id)
            refreshScheduled()
        }
    }

    /** Only a synced message (non-null serverId) can be quoted — the server has to be able to resolve the reference (see MessagesService.send's validation), so a still-pending local-only message can't be replied to yet. */
    fun onReplyToMessage(message: Message) {
        if (message.serverId == null) return
        _replyingToMessage.value = message
    }

    fun onCancelReply() {
        _replyingToMessage.value = null
    }

    /**
     * Handles any picked content — image, generic file, or video — from
     * the composer's single attach button. Video is detected by mime type
     * and routed through metadata extraction first; everything else goes
     * straight through. One button, no separate video-specific UI entry
     * point needed.
     */
    /** Phase 2A: a finished round-video take goes out as a normal video attachment named so the bubble renders it round. */
    fun onSendRoundVideo(file: java.io.File) {
        onSendAttachment(android.net.Uri.fromFile(file).toString(), "video/mp4", RoundVideo.filename(System.currentTimeMillis()))
    }

    /** Headers for streaming a received video (see LocalMediaAuth). */
    val mediaAuthHeaders: suspend () -> Map<String, String> = { withContext(Dispatchers.IO) { voicePlayback.authHeaders() } }

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

    /** Phase 2A: photos from the gallery composer: one attachment message each (captioned individually). */
    fun onSendPhotos(items: List<PhotoOutgoing>) {
        viewModelScope.launch {
            items.forEach { p -> chatRepository.sendAttachment(conversationId, p.contentUri, p.mimeType, p.filename, caption = p.caption) }
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
                        waveform = result.waveform.takeIf { it.isNotEmpty() },
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

    /** Phase 1B: tap on a failed send. */
    fun onRetryFailed(message: Message) {
        viewModelScope.launch { chatRepository.retryFailedMessage(message.clientMsgId) }
    }

    /** Phase 1B: long-press + confirm on a failed send. */
    fun onDiscardFailed(message: Message) {
        viewModelScope.launch { chatRepository.discardFailedMessage(message.clientMsgId) }
    }

    /** Phase 1B: tap on the upload progress ring. */
    fun onCancelUpload(message: Message) {
        chatRepository.cancelUpload(message.clientMsgId)
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

    /** Phase 2A (selection mode): forwards each selected text message, oldest first so they land in order. */
    fun onForwardMany(messages: List<Message>, targetConversationId: String) {
        val ordered = messages.filter { it.serverId != null }.sortedBy { it.createdAtEpochMillis }
        viewModelScope.launch {
            ordered.forEach { m ->
                val serverId = m.serverId ?: return@forEach
                chatRepository.forwardMessage(conversationId, serverId, targetConversationId)
            }
        }
    }

    /** Phase 2A (selection mode): deletes the selected messages; the UI only offers this when all are own and synced. */
    fun onDeleteMany(messages: List<Message>) {
        viewModelScope.launch {
            messages.forEach { m ->
                val serverId = m.serverId ?: return@forEach
                chatRepository.deleteMessage(conversationId, serverId)
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

    private companion object {
        const val INITIAL_WINDOW = 300
        const val WINDOW_STEP = 300
    }
}
