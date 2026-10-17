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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import com.omidgame.mench.core.ui.theme.MenchBrand
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Brush
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.Spacer
import com.omidgame.mench.core.ui.components.UserAvatar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import com.omidgame.mench.core.ui.components.MenchSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.omidgame.mench.feature.product.ProductMessageActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.omidgame.mench.feature.chat.domain.ReactionSummary

/** Quick-react presets shown on long-press — not meant to be exhaustive (spec section 20's proprietary MENCH emoji system is a later phase); these six cover the common WhatsApp/Telegram-style reaction set. */
/** Settings Phase 2: the user's quick reactions (up to 6); defaults equal the old hard-coded list. */
private val QUICK_REACTIONS: List<String>
    get() = ChatSettingsStore.current.quickReactions

/** Reactions that make hearts float up the screen when someone adds one. */
private val HEART_REACTIONS = setOf("\u2764\uFE0F", "\u2764", "\uD83D\uDE0D", "\uD83E\uDD70", "\uD83D\uDE18", "\uD83D\uDC95", "\uD83D\uDC97", "\uD83D\uDC96")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    isRecording: Boolean,
    playbackState: PlaybackState,
    otherPresence: com.omidgame.mench.feature.chat.domain.UserPresence? = null,
    onSend: (String) -> Unit,
    onSendAttachment: (contentUri: String, mimeType: String, originalFilename: String) -> Unit,
    onSendPhotos: (List<PhotoOutgoing>) -> Unit = {},
    onStartRecording: () -> Boolean,
    onStopRecordingAndSend: () -> Unit,
    onCancelRecording: () -> Unit,
    onTogglePlayback: (sourceId: String, localPath: String?, remoteUrl: String?) -> Unit,
    onMessagesRead: (Long) -> Unit,
    onStartEdit: (Message) -> Unit,
    onCancelEdit: () -> Unit,
    onSubmitEdit: (String) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onRetryFailed: (Message) -> Unit = {},
    onDiscardFailed: (Message) -> Unit = {},
    onCancelUpload: (Message) -> Unit = {},
    onReact: (Message, String) -> Unit,
    onForward: (Message, String) -> Unit,
    replyingToMessage: Message? = null,
    onReplyToMessage: (Message) -> Unit = {},
    onCancelReply: () -> Unit = {},
    onBackClick: () -> Unit,
    onGroupInfoClick: () -> Unit,
    onMediaGalleryClick: () -> Unit = {},
    /** Phase 4C: current disappearing-messages timer (null = off) and the tap on the timer action. */
    disappearingSeconds: Int? = null,
    onDisappearingClick: () -> Unit = {},
    onVoiceCallClick: () -> Unit = {},
    onVideoCallClick: () -> Unit = {},
    missYouEvents: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    onSendMissYou: () -> Unit = {},
    /** Phase 3A: partner's live heartbeats (held duration in ms) and the hold-to-send callback. */
    heartbeatEvents: kotlinx.coroutines.flow.Flow<Long> = kotlinx.coroutines.flow.emptyFlow(),
    onSendHeartbeat: (durationMs: Long, onResult: (delivered: Boolean) -> Unit) -> Unit = { _, _ -> },
    /** Phase 3A: effects another member picked by hand (MessageEffect.wire) and the send callback for ours. */
    screenEffectEvents: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow(),
    onSendScreenEffect: (String) -> Unit = {},
    togetherEvents: kotlinx.coroutines.flow.Flow<com.omidgame.mench.feature.chat.domain.TogetherSignal> = kotlinx.coroutines.flow.emptyFlow(),
    onSendTogether: (kind: String, attachmentId: String?, positionMs: Long, playing: Boolean) -> Unit = { _, _, _, _ -> },
    onSetSecureChat: suspend (Boolean) -> Boolean = { false },
    letters: List<com.omidgame.mench.feature.chat.domain.Letter> = emptyList(),
    memories: List<com.omidgame.mench.feature.chat.domain.Memory> = emptyList(),
    counters: List<com.omidgame.mench.feature.chat.domain.Counter> = emptyList(),
    onRefreshCounters: () -> Unit = {},
    onCreateCounter: (title: String, emoji: String?, kind: String, anchorDateIso: String?, theme: Int) -> Unit = { _, _, _, _, _ -> },
    onBumpCounter: (counterId: String, delta: Int) -> Unit = { _, _ -> },
    onDeleteCounter: (String) -> Unit = {},
    canvasStrokes: List<com.omidgame.mench.feature.chat.domain.CanvasStroke> = emptyList(),
    onRefreshCanvas: () -> Unit = {},
    onAppendStrokes: (List<com.omidgame.mench.feature.chat.domain.CanvasStroke>) -> Unit = {},
    onClearCanvas: () -> Unit = {},
    stories: List<com.omidgame.mench.feature.chat.domain.Story> = emptyList(),
    onRefreshStories: () -> Unit = {},
    vaultItems: List<com.omidgame.mench.feature.chat.domain.VaultItem> = emptyList(),
    onRefreshVault: () -> Unit = {},
    quizzes: List<com.omidgame.mench.feature.chat.domain.QuizSummary> = emptyList(),
    onRefreshQuizzes: () -> Unit = {},
    yearMessages: com.omidgame.mench.feature.chat.domain.YearMessageStats? = null,
    albumItems: List<com.omidgame.mench.feature.chat.domain.AlbumItem> = emptyList(),
    onRefreshAlbum: () -> Unit = {},
    onAddAlbumPhoto: (com.omidgame.mench.feature.chat.domain.PickedPhoto, String?) -> Unit = { _, _ -> },
    onReactAlbumItem: (String, String?) -> Unit = { _, _ -> },
    onDeleteAlbumItem: (String) -> Unit = {},
    onLoadYearMessages: () -> Unit = {},
    onCreateQuiz: (String, List<com.omidgame.mench.feature.chat.domain.QuizQuestion>) -> Unit = { _, _ -> },
    onLoadQuiz: (String, (com.omidgame.mench.feature.chat.domain.QuizDetail?) -> Unit) -> Unit = { _, _ -> },
    onSubmitQuiz: (String, List<Int>, (com.omidgame.mench.feature.chat.domain.QuizResult?) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteQuiz: (String) -> Unit = {},
    onAddVaultItem: (String) -> Unit = {},
    onDeleteVaultItem: (String) -> Unit = {},
    onCreateStory: (body: String, emoji: String?, palette: Int, photo: com.omidgame.mench.feature.chat.domain.PickedPhoto?) -> Unit = { _, _, _, _ -> },
    onViewStory: (String) -> Unit = {},
    onDeleteStory: (String) -> Unit = {},
    onRefreshMemories: () -> Unit = {},
    onDeleteMemory: (String) -> Unit = {},
    onCreateMemory: (String, String?, String, String?, String?, String?) -> Unit = { _, _, _, _, _, _ -> },
    affectionEvents: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow(),
    onSendAffection: (String) -> Unit = {},
    question: com.omidgame.mench.feature.chat.domain.Question? = null,
    onRefreshQuestion: () -> Unit = {},
    onAnswerQuestion: (String) -> Unit = {},
    wishes: List<com.omidgame.mench.feature.chat.domain.Wish> = emptyList(),
    onRefreshWishes: () -> Unit = {},
    onAddWish: (String) -> Unit = {},
    onToggleWish: (com.omidgame.mench.feature.chat.domain.Wish) -> Unit = {},
    onDeleteWish: (String) -> Unit = {},
    onPinMessage: (Message) -> Unit = {},
    onUnpinMessage: () -> Unit = {},
    onLoadLinkPreview: suspend (String) -> com.omidgame.mench.feature.chat.domain.LinkPreview? = { null },
    moods: com.omidgame.mench.feature.chat.domain.Moods? = null,
    onSetMood: (String, String?) -> Unit = { _, _ -> },
    onRefreshLetters: () -> Unit = {},
    onOpenLetter: (String) -> Unit = {},
    onCreateLetter: (String, String, String?, com.omidgame.mench.feature.chat.domain.PickedPhoto?) -> Unit = { _, _, _, _ -> },
    onSetUsDates: (togetherSince: String?, nextMeetDate: String?) -> Unit = { _, _ -> },
    onLoadOlder: () -> Unit = {},
    onLoadAll: () -> Unit = {},
    // Phase 2A
    selfUserId: String? = null,
    recorderLive: com.omidgame.mench.core.media.RecorderLive = com.omidgame.mench.core.media.RecorderLive(),
    voiceUi: VoiceUi? = null,
    onForwardMany: (List<Message>, String) -> Unit = { _, _ -> },
    onDeleteMany: (List<Message>) -> Unit = {},
    pollUi: PollUi? = null,
    onCreatePoll: (String, List<String>, Boolean) -> Unit = { _, _, _ -> },
    mediaAuthHeaders: suspend () -> Map<String, String> = { emptyMap() },
    onSendRoundVideo: (java.io.File) -> Unit = {},
    scheduled: List<com.omidgame.mench.feature.chat.domain.ScheduledMessage> = emptyList(),
    scheduleErrors: kotlinx.coroutines.flow.SharedFlow<Int>? = null,
    onSendSilently: (String) -> Unit = {},
    onScheduleMessage: (String, Long, Boolean) -> Unit = { _, _, _ -> },
    onCancelScheduled: (com.omidgame.mench.feature.chat.domain.ScheduledMessage) -> Unit = {},
    onRefreshScheduled: () -> Unit = {},
    canPost: Boolean = true,
    onRefreshGroupInfo: () -> Unit = {},
    onLoadSeenBy: suspend (Message) -> List<com.omidgame.mench.feature.chat.domain.SeenBy>? = { null },
    onLoadEditHistory: suspend (Message) -> List<com.omidgame.mench.feature.chat.domain.EditVersion>? = { null },
    onTranslate: suspend (Message) -> String? = { null },
) {
    val context = LocalContext.current
    var showUsDialog by remember { mutableStateOf(false) }
    val usSettings = rememberUsSettings()

    // In-chat search: null = closed. Matches are indices into state.messages (one list item per message).
    var searchQuery by remember { mutableStateOf<String?>(null) }
    var searchCursor by remember { mutableStateOf(0) }
    val searchMatches = remember(searchQuery, state.messages) {
        val q = searchQuery?.trim().orEmpty()
        if (q.isEmpty()) emptyList()
        else state.messages.mapIndexedNotNull { idx, m ->
            if (m.deletedAtEpochMillis == null && m.body?.contains(q, ignoreCase = true) == true) idx else null
        }
    }
    val currentMatchIndex = searchMatches.getOrNull(searchCursor.coerceIn(0, (searchMatches.size - 1).coerceAtLeast(0)))
    var showLetters by remember { mutableStateOf(false) }
    // Phase 2A: multi-select (clientMsgIds, insertion-ordered) and the "who reacted" sheet.
    var selectedIds by remember(state.conversationId) { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var reactorsFor by remember { mutableStateOf<Message?>(null) }
    var seenByFor by remember { mutableStateOf<Message?>(null) }
    var editHistoryFor by remember { mutableStateOf<Message?>(null) }
    var translateFor by remember { mutableStateOf<Message?>(null) }
    var forwardMany by remember { mutableStateOf<List<Message>?>(null) }
    var confirmDeleteMany by remember { mutableStateOf<List<Message>?>(null) }
    val selectedMessages = remember(selectedIds, state.messages) {
        state.messages.filter { it.clientMsgId in selectedIds }
    }
    // Drop ids of messages that disappeared (e.g. deleted by the sender) so the bar never counts ghosts.
    LaunchedEffect(state.messages) {
        if (selectedIds.isNotEmpty()) {
            val alive = state.messages.mapTo(HashSet()) { it.clientMsgId }
            val kept = selectedIds.filterTo(LinkedHashSet()) { it in alive }
            if (kept.size != selectedIds.size) selectedIds = kept
        }
    }
    androidx.activity.compose.BackHandler(enabled = selectionMode) { selectedIds = emptySet() }
    // Channel mode can be switched by an admin while this screen is open: re-check when the screen resumes.
    LaunchedEffect(state.conversationId, state.messages.size) { if (state.isGroupConversation) onRefreshGroupInfo() }
    // A scheduled message that just went out disappears from the pending list: refresh when the message count changes.
    LaunchedEffect(state.messages.size) { onRefreshScheduled() }
    LaunchedEffect(scheduleErrors) {
        scheduleErrors?.collect { res -> android.widget.Toast.makeText(context, res, android.widget.Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(pollUi) {
        pollUi?.errors?.collect { res -> android.widget.Toast.makeText(context, res, android.widget.Toast.LENGTH_SHORT).show() }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLinkPreviewLoader provides onLoadLinkPreview,
        LocalVoiceUi provides voiceUi,
        LocalPollUi provides pollUi,
        LocalMediaAuth provides mediaAuthHeaders,
        LocalHashtagSearch provides { tag -> searchQuery = tag; searchCursor = 0 },
    ) {
    var showMemories by remember { mutableStateOf(false) }
    var showCounters by remember { mutableStateOf(false) }
    var showCanvas by remember { mutableStateOf(false) }
    var showStories by remember { mutableStateOf(false) }
    var showVault by remember { mutableStateOf(false) }
    var showGames by remember { mutableStateOf(false) }
    var showAlbum by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showYear by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }
    var showQuestion by remember { mutableStateOf(false) }
    var showWishlist by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listScope = androidx.compose.runtime.rememberCoroutineScope()
    // Jump-to-reply (Phase 2A): the message we jumped to flashes for a moment so the eye finds it.
    var flashId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flashId) {
        if (flashId != null) {
            kotlinx.coroutines.delay(1_400)
            flashId = null
        }
    }
    var unseenCount by remember { mutableStateOf(0) }
    var lastSeenMessageId by remember { mutableStateOf<String?>(null) }
    val atBottom by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }
    // Windowed history (Phase 1B): reaching the top loads another page of older messages and keeps the
    // viewport anchored on the message that was at the top, so nothing jumps.
    var anchorKey by remember { mutableStateOf<String?>(null) }
    var anchorOffset by remember { mutableStateOf(0) }
    val nearTop by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex <= 3 } }
    LaunchedEffect(nearTop, state.hasOlderMessages) {
        // lastSeenMessageId != null means the initial "open at newest" positioning already happened.
        if (nearTop && state.hasOlderMessages && lastSeenMessageId != null && !atBottom && state.messages.isNotEmpty()) {
            anchorKey = state.messages.getOrNull(listState.firstVisibleItemIndex)?.clientMsgId
            anchorOffset = listState.firstVisibleItemScrollOffset
            onLoadOlder()
        }
    }
    LaunchedEffect(state.messages.firstOrNull()?.clientMsgId) {
        val key = anchorKey ?: return@LaunchedEffect
        val idx = state.messages.indexOfFirst { it.clientMsgId == key }
        if (idx >= 0) listState.scrollToItem(idx, anchorOffset)
        anchorKey = null
    }
    // In-chat search looks through the whole history, so load all of it as soon as search opens.
    LaunchedEffect(searchQuery != null) { if (searchQuery != null) onLoadAll() }
    // Open at the newest message, follow new ones while you're at the bottom,
    // and count the ones that arrive while you've scrolled up reading history.
    val soundContext = LocalContext.current
    val albums = remember(state.messages) { Albums.compute(state.messages) }
    var showTogetherPicker by remember { mutableStateOf(false) }
    if (!state.isGroupConversation) {
        TogetherHost(
            messages = state.messages,
            signals = togetherEvents,
            showPicker = showTogetherPicker,
            onPickerDismiss = { showTogetherPicker = false },
            onSend = onSendTogether,
        )
    }
    LaunchedEffect(state.messages.lastOrNull()?.clientMsgId) {
        val newest = state.messages.lastOrNull() ?: return@LaunchedEffect
        val lastIndex = state.messages.size - 1
        when {
            lastSeenMessageId == null -> listState.scrollToItem(lastIndex)
            newest.clientMsgId != lastSeenMessageId -> {
                com.omidgame.mench.core.sound.MenchSounds.play(
                    soundContext,
                    if (newest.isOwn) com.omidgame.mench.core.sound.MenchSound.SENT else com.omidgame.mench.core.sound.MenchSound.RECEIVED,
                )
                if (atBottom || newest.isOwn) listState.animateScrollToItem(lastIndex) else unseenCount++
            }
        }
        lastSeenMessageId = newest.clientMsgId
    }
    LaunchedEffect(atBottom) { if (atBottom) unseenCount = 0 }
    // TalkBack live announcement (Phase 4A): a message that arrives while the chat is open is read
    // out ("sender, text, time"). The first value seen on open is history and stays silent.
    val a11yNewest = state.messages.lastOrNull()
    val a11yNewestText = if (a11yNewest != null && !a11yNewest.isOwn && a11yNewest.deletedAtEpochMillis == null) {
        messageA11yDescription(a11yNewest, null)
    } else {
        null
    }
    val a11yHost = androidx.compose.ui.platform.LocalView.current
    var a11yArmed by remember { mutableStateOf(false) }
    LaunchedEffect(a11yNewest?.clientMsgId) {
        if (a11yArmed && a11yNewestText != null) {
            val manager = a11yHost.context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
                as? android.view.accessibility.AccessibilityManager
            if (manager?.isEnabled == true) a11yHost.announceForAccessibility(a11yNewestText)
        }
        a11yArmed = true
    }
    val chatPrimary = MaterialTheme.colorScheme.primary
    val chatBackground = MaterialTheme.colorScheme.background
    // A soft top-to-bottom wash of the accent behind the conversation.
    val (wallpaper, chatAccentOverride) = rememberChatTheme(state.conversationId)
    var showChatTheme by remember { mutableStateOf(false) }
    var showChatSecurity by remember { mutableStateOf(false) }
    if (showChatSecurity) ChatSecurityDialog(conversationId = state.conversationId, isGroup = state.isGroupConversation, onSetSecureChat = onSetSecureChat, onDismiss = { showChatSecurity = false })
    if (!state.isGroupConversation) KeyChangeWarning(conversationId = state.conversationId, onViewCode = { showChatSecurity = true })
    if (showChatTheme) ChatThemeDialog(conversationId = state.conversationId, onDismiss = { showChatTheme = false })
    val chatBackdrop = remember(chatPrimary, chatBackground, wallpaper, chatAccentOverride) { wallpaper.brush(chatBackground, chatAccentOverride ?: chatPrimary) }
    var heartGlyphs by remember { mutableStateOf(glyphsFor("love")) }
    var heartBurst by remember { mutableStateOf(0) }
    // Heart-ish reaction added to a message we already knew about -> float hearts.
    // Tracked per message so opening a chat or loading older history never triggers it.
    val knownHeartCounts = remember { mutableMapOf<String, Int>() }
    val knownReactionTotals = remember { mutableMapOf<String, Int>() }
    LaunchedEffect(state.messages) {
        var increased = false
        var newOtherEmoji: String? = null
        state.messages.forEach { message ->
            val count = message.reactions.filter { it.emoji in HEART_REACTIONS }.sumOf { it.userIds.size }
            val previous = knownHeartCounts[message.clientMsgId]
            val heartUp = previous != null && count > previous
            if (heartUp) increased = true
            knownHeartCounts[message.clientMsgId] = count
            // Phase 2A: a non-heart reaction added to a message we already knew about bursts that emoji.
            val total = message.reactions.sumOf { it.userIds.size }
            val previousTotal = knownReactionTotals[message.clientMsgId]
            if (previousTotal != null && total > previousTotal && !heartUp) {
                newOtherEmoji = message.reactions.lastOrNull { it.emoji !in HEART_REACTIONS }?.emoji ?: newOtherEmoji
            }
            knownReactionTotals[message.clientMsgId] = total
        }
        if (increased) {
            heartGlyphs = glyphsFor("love")
            heartBurst++
            vibrateAffection(context, "kiss")
        } else {
            val burstEmoji = newOtherEmoji
            if (burstEmoji != null) {
                heartGlyphs = listOf(burstEmoji)
                heartBurst++
            }
        }
    }
    LaunchedEffect(affectionEvents) {
        affectionEvents.collect { kind ->
            heartGlyphs = glyphsFor(kind)
            heartBurst++
            vibrateAffection(context, kind)
        }
    }
    LaunchedEffect(missYouEvents) {
        missYouEvents.collect {
            heartGlyphs = glyphsFor("love")
            heartBurst++
            vibrateHeartbeat(context)
        }
    }

    // --- Phase 3A: live heartbeat + full-screen message effects ---
    var activeEffect by remember { mutableStateOf<MessageEffect?>(null) }
    var effectKey by remember { mutableStateOf(0) }
    fun playEffect(effect: MessageEffect) {
        activeEffect = effect
        effectKey++
    }
    val effectsEnabled = rememberEffectSetting(MessageEffectSettings.KEY_EFFECTS)
    val heartbeatVibration = rememberEffectSetting(MessageEffectSettings.KEY_HEARTBEAT_VIBRATION)
    var heartbeatKey by remember { mutableStateOf(0) }
    var heartbeatMillis by remember { mutableStateOf(0L) }
    LaunchedEffect(heartbeatEvents, heartbeatVibration) {
        heartbeatEvents.collect { ms ->
            heartbeatMillis = ms
            heartbeatKey++
            if (heartbeatVibration) vibrateHeartbeatFor(context, ms)
            // Both of you were holding the heart at about the same time: celebrate together.
            if (HeartbeatSync.isSynced(android.os.SystemClock.elapsedRealtime()) && effectsEnabled) {
                playEffect(MessageEffect.HEARTS)
                android.widget.Toast.makeText(context, R.string.heartbeat_synced, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Incoming words that trigger an effect. The first batch of messages is history and never plays;
    // later batches only play for recent messages, so scrolling back through old chat stays quiet.
    val effectSeenIds = remember { mutableSetOf<String>() }
    var effectsPrimed by remember { mutableStateOf(false) }
    LaunchedEffect(state.messages) {
        if (!effectsPrimed) {
            if (state.messages.isNotEmpty()) {
                state.messages.forEach { effectSeenIds.add(it.clientMsgId) }
                effectsPrimed = true
            }
            return@LaunchedEffect
        }
        var found: MessageEffect? = null
        val now = System.currentTimeMillis()
        state.messages.forEach { m ->
            val isNew = effectSeenIds.add(m.clientMsgId)
            if (isNew && found == null && !m.isOwn && m.deletedAtEpochMillis == null && now - m.createdAtEpochMillis < 60_000L) {
                found = MessageEffectTriggers.detect(m.body, MessageEffectSettings.customTriggers(context))
            }
        }
        val effect = found
        if (effect != null && effectsEnabled) playEffect(effect)
    }
    // Phase 3B: on special days play one gentle effect the first time a chat is opened that day.
    LaunchedEffect(effectsEnabled) {
        val today = java.time.LocalDate.now()
        val seasonal = BirthdayStore.effectToday(context, today) ?: seasonalEffectFor(today)
        if (effectsEnabled && seasonal != null && MessageEffectSettings.claimSeasonalPlay(context, today.toString())) {
            kotlinx.coroutines.delay(700)
            playEffect(seasonal)
        }
    }
    LaunchedEffect(screenEffectEvents, effectsEnabled) {
        screenEffectEvents.collect { wire ->
            val effect = MessageEffect.fromWire(wire)
            if (effect != null && effectsEnabled) playEffect(effect)
        }
    }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (selectionMode) {
                    SelectionTopBar(
                        selected = selectedMessages,
                        onClose = { selectedIds = emptySet() },
                        onForward = { forwardMany = selectedMessages.filter { it.canForward() } },
                        onDelete = { confirmDeleteMany = selectedMessages.filter { it.isOwn && it.serverId != null && it.deletedAtEpochMillis == null } },
                        onPin = {
                            selectedMessages.singleOrNull()?.let { onPinMessage(it) }
                            selectedIds = emptySet()
                        },
                        onCopied = { selectedIds = emptySet() },
                    )
                } else TopAppBar(
                    modifier = Modifier.background(MenchBrand.heroBrush()),
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                            UserAvatar(
                                userId = state.otherUserId,
                                version = state.otherUserAvatarVersion,
                                name = state.headerTitle,
                                size = 38.dp,
                                sharedKey = state.conversationId.takeIf { it.isNotBlank() },
                                modifier = Modifier.border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                            )
                            if (otherPresence?.online == true) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(11.dp)
                                        .background(Color(0xFF3DDC84), CircleShape)
                                        .border(1.5.dp, Color.White, CircleShape),
                                )
                            }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                            (if (state.isSavedMessages) stringResource(R.string.saved_messages_title) else state.headerTitle ?: "") +
                                (moods?.other.todayOrNull()?.let { "  " + it.emoji } ?: ""),
                        )
                            val subtitle = when {
                                state.isOtherTyping && !state.isGroupConversation -> stringResource(R.string.chat_typing_indicator)
                                otherPresence?.online == true -> stringResource(R.string.presence_online)
                                otherPresence?.lastSeenAtEpochMillis != null -> {
                                    val seen = LastSeenFormatter.format(otherPresence.lastSeenAtEpochMillis)
                                    when (seen.day) {
                                        LastSeenFormatter.Day.TODAY -> stringResource(R.string.presence_last_seen_today, seen.time)
                                        LastSeenFormatter.Day.YESTERDAY -> stringResource(R.string.presence_last_seen_yesterday, seen.time)
                                        LastSeenFormatter.Day.OTHER -> stringResource(R.string.presence_last_seen_date, seen.date)
                                    }
                                }
                                else -> null
                            }
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (otherPresence?.online == true) 1f else 0.78f),
                                )
                            }
                        }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                        }
                    },
                    actions = {
                        // Every conversation (direct or group) can have media
                        // in it, unlike the calls/group-info actions below
                        // which are conditional — so this one has no isGroup
                        // gate.
                        IconButton(onClick = { searchQuery = if (searchQuery == null) "" else null }) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        }
                        IconButton(onClick = { showChatSecurity = true }) {
                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.chat_security_title))
                        }
                        IconButton(onClick = { showChatTheme = true }) {
                            Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.chat_theme_action))
                        }
                        IconButton(onClick = onDisappearingClick) {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Timer,
                                contentDescription = stringResource(R.string.disappearing_title),
                                tint = if (disappearingSeconds != null && disappearingSeconds > 0) Color.White else Color.White.copy(alpha = 0.7f),
                            )
                        }
                        IconButton(onClick = onMediaGalleryClick) {
                            Icon(Icons.Filled.PermMedia, contentDescription = stringResource(R.string.media_gallery_title))
                        }
                        // Calls are 1:1 only (see backend CallsService.initiate) —
                        // a group conversation has no single "other member" to
                        // call, so these only make sense for a direct chat.
                        if (!state.isGroupConversation && !state.isSavedMessages) {
                            IconButton(onClick = { showUsDialog = true }) {
                                Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.us_title), tint = Color.White)
                            }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Real blur (Android 12+) of the chat while the private vault is open on top of it.
                    .then(if (showVault) Modifier.blur(18.dp) else Modifier)
                    .background(chatBackdrop)
                    .livingLayer(wallpaper.living)
                    .padding(padding),
            ) {
                com.omidgame.mench.feature.privacy.presentation.DisappearingBanner(seconds = disappearingSeconds)
                state.pinnedMessage?.let { pinned ->
                    PinnedBanner(
                        text = RichBody.kindOf(pinned.body)?.let { stringResource(richKindLabel(it)) }
                            ?: pinned.body?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.pin_attachment),
                        onClick = {
                            val index = state.messages.indexOfFirst { it.clientMsgId == pinned.clientMsgId }
                            if (index >= 0) listScope.launch { listState.animateScrollToItem(index) }
                        },
                        onUnpin = onUnpinMessage,
                    )
                }
                // Unread divider: remember the conversation's read position from the moment the chat
                // opened; the first received message past it gets a "unread messages" line above it.
                val thisConversation = state.conversations.firstOrNull { it.id == state.conversationId }
                var readBaseline by remember(state.conversationId) { mutableStateOf<Long?>(null) }
                LaunchedEffect(thisConversation != null, state.conversationId) {
                    if (thisConversation != null && readBaseline == null) readBaseline = thisConversation.lastReadSequence
                }
                val firstUnreadId = readBaseline?.let { base ->
                    state.messages.firstOrNull { !it.isOwn && (it.sequence ?: 0L) > base }?.clientMsgId
                }
                // Phase 2: open at the first unread message (once per opening) instead of the very bottom.
                var unreadJumpDone by remember(state.conversationId) { mutableStateOf(false) }
                LaunchedEffect(firstUnreadId, readBaseline, state.messages.isNotEmpty()) {
                    if (unreadJumpDone || readBaseline == null || state.messages.isEmpty()) return@LaunchedEffect
                    val idx = state.messages.indexOfFirst { it.clientMsgId == firstUnreadId }
                    if (idx > 0) {
                        listState.scrollToItem(idx, scrollOffset = -48)
                        unseenCount = state.messages.drop(idx).count { !it.isOwn }
                    }
                    unreadJumpDone = true
                }
                if (searchQuery != null) {
                    LaunchedEffect(currentMatchIndex) {
                        currentMatchIndex?.let { listState.animateScrollToItem(it) }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.TextField(
                            value = searchQuery.orEmpty(),
                            onValueChange = { searchQuery = it; searchCursor = 0 },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        Text(
                            text = if (searchMatches.isEmpty()) "0" else "${searchCursor.coerceIn(0, searchMatches.size - 1) + 1}/${searchMatches.size}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        IconButton(
                            enabled = searchMatches.isNotEmpty(),
                            onClick = { searchCursor = if (searchCursor + 1 >= searchMatches.size) 0 else searchCursor + 1 },
                        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) }
                        IconButton(
                            enabled = searchMatches.isNotEmpty(),
                            onClick = { searchCursor = if (searchCursor - 1 < 0) searchMatches.size - 1 else searchCursor - 1 },
                        ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) }
                        IconButton(onClick = { searchQuery = null }) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = null)
                        }
                    }
                }
                com.omidgame.mench.core.ui.components.ConnectionBannerHost()
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    itemsIndexed(state.messages, key = { _, m -> m.clientMsgId }) { index, message ->
                        // Phase 2A: photos of one album are drawn once, inside the first photo's bubble.
                        if (message.clientMsgId in albums.followers) return@itemsIndexed
                        Column(modifier = Modifier.animateItem().popInIfNew(message.createdAtEpochMillis)) {
                        val previous = state.messages.getOrNull(index - 1)
                        val next = state.messages.getOrNull(index + 1)
                        // Consecutive messages from one sender within 3 minutes on the same day
                        // are drawn as one visual group: tight spacing, tail only on the last.
                        fun joined(a: Message?, b: Message?): Boolean =
                            a != null && b != null &&
                                a.senderId == b.senderId && a.isOwn == b.isOwn &&
                                kotlin.math.abs(b.createdAtEpochMillis - a.createdAtEpochMillis) < 3 * 60_000L &&
                                ChatDateFormatter.dayKey(a.createdAtEpochMillis) == ChatDateFormatter.dayKey(b.createdAtEpochMillis)
                        val groupedWithPrev = joined(previous, message)
                        val groupedWithNext = joined(message, next)
                        if (previous == null ||
                            ChatDateFormatter.dayKey(previous.createdAtEpochMillis) != ChatDateFormatter.dayKey(message.createdAtEpochMillis)
                        ) {
                            DateChip(message.createdAtEpochMillis)
                        }
                        if (message.clientMsgId == firstUnreadId) {
                            UnreadDivider()
                        }
                        val isSelected = message.clientMsgId in selectedIds
                        val toggleSelected = {
                            selectedIds = if (isSelected) selectedIds - message.clientMsgId else selectedIds + message.clientMsgId
                        }
                        val rowTint by androidx.compose.animation.animateColorAsState(
                            targetValue = when {
                                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                message.clientMsgId == flashId -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                                index == currentMatchIndex -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            },
                            label = "rowTint",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowTint, RoundedCornerShape(12.dp))
                                // In selection mode a tap anywhere on the row toggles it (not only on the bubble).
                                .clickable(enabled = selectionMode, onClick = toggleSelected),
                        ) {
                        SwipeToReply(
                            enabled = message.serverId != null && message.deletedAtEpochMillis == null && !selectionMode,
                            onReply = { onReplyToMessage(message) },
                        ) {
                        MessageBubble(
                            message = message,
                            // Only shown for a received message in a group — your
                            // own messages need no label, and a direct
                            // conversation only ever has one other sender anyway
                            // (see ChatUiState.isGroupConversation).
                            showTail = !groupedWithNext,
                            topSpacing = if (groupedWithPrev) 1.5.dp else 6.dp,
                            senderLabel = if (state.isGroupConversation && !message.isOwn && !groupedWithPrev) {
                                state.memberDisplayNames[message.senderId]
                            } else {
                                null
                            },
                            // Resolved from state.messages, already in memory —
                            // no separate lookup query, same reasoning as
                            // MediaGalleryViewModel's doc comment on reusing
                            // this one already-live list rather than adding a
                            // second source of truth.
                            repliedMessage = message.replyToMessageId?.let { replyId ->
                                state.messages.firstOrNull { it.serverId == replyId }
                            },
                            playbackState = playbackState,
                            onImageClick = { viewerAttachment = it },
                            albumItems = albums.leaders[message.clientMsgId],
                            onVideoClick = { playerAttachment = it },
                            onTogglePlayback = onTogglePlayback,
                            onStartEdit = onStartEdit,
                            onDeleteMessage = onDeleteMessage,
                            onRetryFailed = onRetryFailed,
                            onDiscardFailed = onDiscardFailed,
                            onCancelUpload = onCancelUpload,
                            onReact = onReact,
                            onForwardRequested = { forwardTarget = it },
                            onReplyRequested = onReplyToMessage,
                            onPinRequested = onPinMessage,
                            selectionMode = selectionMode,
                            onToggleSelect = toggleSelected,
                            onEnterSelection = { selectedIds = selectedIds + it.clientMsgId },
                            onDoubleTapReact = { onReact(it, ChatPrefs.doubleTapReaction(context)) },
                            onShowReactors = { reactorsFor = it },
                            isGroupChat = state.isGroupConversation,
                            onShowSeenBy = { seenByFor = it },
                            onShowEditHistory = { editHistoryFor = it },
                            onShowTranslate = { translateFor = it },
                            onJumpToMessage = { target ->
                                val at = state.messages.indexOfFirst { it.clientMsgId == target.clientMsgId }
                                if (at >= 0) {
                                    flashId = target.clientMsgId
                                    listScope.launch { listState.animateScrollToItem(at) }
                                }
                            },
                        )
                        }
                        }
                        }
                    }
                    if (state.isOtherTyping) {
                        item { TypingDots() }
                    }
                }
                // Phase 2: floating date chip while scrolling (shows the day of the topmost visible message).
                var chipVisible by remember { mutableStateOf(false) }
                LaunchedEffect(listState.isScrollInProgress) {
                    if (listState.isScrollInProgress) chipVisible = true
                    else { kotlinx.coroutines.delay(1_200); chipVisible = false }
                }
                val topMessageTime by remember(state.messages) {
                    androidx.compose.runtime.derivedStateOf { state.messages.getOrNull(listState.firstVisibleItemIndex)?.createdAtEpochMillis }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = chipVisible && topMessageTime != null,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                ) {
                    topMessageTime?.let { DateChip(it) }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    enter = androidx.compose.animation.scaleIn(com.omidgame.mench.core.ui.components.MenchMotion.bouncy(), initialScale = 0.5f) +
                        androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut(targetScale = 0.5f) + androidx.compose.animation.fadeOut(),
                ) {
                    androidx.compose.material3.SmallFloatingActionButton(
                        onClick = {
                            listScope.launch { listState.animateScrollToItem((state.messages.size - 1).coerceAtLeast(0)) }
                            unseenCount = 0
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        androidx.compose.material3.BadgedBox(
                            badge = { if (unseenCount > 0) androidx.compose.material3.Badge { Text(unseenCount.toString()) } },
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.chat_scroll_bottom))
                        }
                    }
                }
                }
                if (!canPost) {
                    // Channel mode: only admins post here, so there is nothing to type into.
                    Text(
                        text = stringResource(R.string.channel_admins_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    )
                } else Composer(
                    conversationId = state.conversationId,
                    mentionCandidates = if (state.isGroupConversation) state.memberDisplayNames.values.distinct() else emptyList(),
                    isRecording = isRecording,
                    editingMessage = state.editingMessage,
                    replyingToMessage = replyingToMessage,
                    onCancelReply = onCancelReply,
                    onSend = { body ->
                        onSend(body)
                        // Our own words play the effect right away; the partner's phone detects the same words itself.
                        if (effectsEnabled) MessageEffectTriggers.detect(body, MessageEffectSettings.customTriggers(context))?.let { playEffect(it) }
                    },
                    onSendWithEffect = { body, effect ->
                        onSend(body)
                        onSendScreenEffect(effect.wire)
                        playEffect(effect)
                    },
                    onSendAttachment = onSendAttachment,
                    onSendPhotos = onSendPhotos,
                    onTogether = if (state.isGroupConversation) null else ({ showTogetherPicker = true }),
                    onStartRecording = onStartRecording,
                    onStopRecordingAndSend = onStopRecordingAndSend,
                    onCancelRecording = onCancelRecording,
                    onCancelEdit = onCancelEdit,
                    onSubmitEdit = onSubmitEdit,
                    recorderLive = recorderLive,
                    onCreatePoll = onCreatePoll,
                    onSendRoundVideo = onSendRoundVideo,
                    scheduled = scheduled,
                    onSendSilently = onSendSilently,
                    onScheduleMessage = onScheduleMessage,
                    onCancelScheduled = onCancelScheduled,
                )
            }
        }
        if (usSettings.heartsAnimation) HeartsOverlay(burstKey = heartBurst, glyphs = heartGlyphs)
        MessageEffectOverlay(effect = activeEffect, playKey = effectKey)
        HeartbeatOverlay(playKey = heartbeatKey, durationMs = heartbeatMillis)
    }
    if (showUsDialog) {
        UsDialog(
            togetherSince = state.togetherSince,
            nextMeetDate = state.nextMeetDate,
            onDismiss = { showUsDialog = false },
            onSendHeartbeat = onSendHeartbeat,
            onMissYou = {
                heartGlyphs = glyphsFor("love")
                heartBurst++
                onSendMissYou()
            },
            moods = moods,
            onSetMood = onSetMood,
            onSendAffection = { kind ->
                heartGlyphs = glyphsFor(kind)
                heartBurst++
                vibrateAffection(context, kind)
                onSendAffection(kind)
            },
            onOpenMemories = {
                onRefreshMemories()
                showMemories = true
            },
            onOpenCounters = {
                onRefreshCounters()
                showCounters = true
            },
            onOpenCanvas = {
                onRefreshCanvas()
                showCanvas = true
            },
            onOpenStories = {
                onRefreshStories()
                showStories = true
            },
            onOpenVault = {
                onRefreshVault()
                showVault = true
            },
            onOpenGames = {
                onRefreshQuizzes()
                showGames = true
            },
            onOpenAlbum = {
                onRefreshAlbum()
                showAlbum = true
            },
            onOpenProfile = {
                onRefreshMemories()
                onRefreshCounters()
                onRefreshLetters()
                onRefreshQuestion()
                showProfile = true
            },
            onOpenYear = {
                onRefreshMemories()
                onRefreshCounters()
                onRefreshLetters()
                onLoadYearMessages()
                showYear = true
            },
            onOpenCards = { showCards = true },
            onQuickMessage = { body ->
                onSend(body)
                if (effectsEnabled) MessageEffectTriggers.detect(body, MessageEffectSettings.customTriggers(context))?.let { playEffect(it) }
                showUsDialog = false
            },
            onOpenQuestion = {
                onRefreshQuestion()
                showQuestion = true
            },
            onOpenWishlist = {
                onRefreshWishes()
                showWishlist = true
            },
            onSetUsDates = onSetUsDates,
            onOpenLetters = {
                onRefreshLetters()
                showLetters = true
            },
        )
    }
    if (showQuestion) {
        QuestionDialog(question = question, onDismiss = { showQuestion = false }, onAnswer = onAnswerQuestion)
    }
    if (showWishlist) {
        WishlistDialog(
            wishes = wishes,
            onDismiss = { showWishlist = false },
            onAdd = onAddWish,
            onToggle = onToggleWish,
            onDelete = onDeleteWish,
        )
    }
    if (showYear) {
        YearInLoveDialog(
            stats = computeYearStats(java.time.LocalDate.now(), state.togetherSince, memories, letters, counters),
            messages = yearMessages,
            onDismiss = { showYear = false },
        )
    }
    if (showCards) {
        CoupleCardsDialog(onDismiss = { showCards = false })
    }
    if (showProfile) {
        CoupleProfileDialog(
            profile = buildCoupleProfile(java.time.LocalDate.now(), state.togetherSince, state.nextMeetDate, memories, letters, counters, question),
            partnerId = state.otherUserId,
            partnerAvatarVersion = state.otherUserAvatarVersion,
            partnerName = state.headerTitle,
            onDismiss = { showProfile = false },
        )
    }
    if (showAlbum) {
        AlbumDialog(
            items = albumItems,
            onDismiss = { showAlbum = false },
            onAdd = onAddAlbumPhoto,
            onReact = onReactAlbumItem,
            onDelete = onDeleteAlbumItem,
        )
    }
    if (showGames) {
        GamesHubDialog(
            quizzes = quizzes,
            onDismiss = { showGames = false },
            onCreateQuiz = onCreateQuiz,
            onLoadQuiz = onLoadQuiz,
            onSubmitQuiz = onSubmitQuiz,
            onDeleteQuiz = onDeleteQuiz,
        )
    }
    if (showVault) {
        VaultDialog(
            conversationId = state.conversationId,
            items = vaultItems,
            onDismiss = { showVault = false },
            onAdd = onAddVaultItem,
            onDelete = onDeleteVaultItem,
        )
    }
    if (showStories) {
        StoriesViewer(
            stories = stories,
            onDismiss = { showStories = false },
            onCreate = onCreateStory,
            onView = onViewStory,
            onDelete = onDeleteStory,
            onReact = { emoji, story ->
                onSend(emoji + " ↩ «" + story.body.take(60) + "»")
                android.widget.Toast.makeText(context, R.string.stories_reaction_sent, android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (showCanvas) {
        SharedCanvasDialog(
            strokes = canvasStrokes,
            onDismiss = { showCanvas = false },
            onAddStrokes = onAppendStrokes,
            onClear = onClearCanvas,
        )
    }
    if (showCounters) {
        CountersDialog(
            counters = counters,
            onDismiss = { showCounters = false },
            onCreate = onCreateCounter,
            onBump = onBumpCounter,
            onDelete = onDeleteCounter,
        )
    }
    if (showMemories) {
        MemoriesDialog(
            memories = memories,
            onDismiss = { showMemories = false },
            onDelete = onDeleteMemory,
            onCreate = onCreateMemory,
        )
    }
    if (showLetters) {
        LettersDialog(
            letters = letters,
            onDismiss = { showLetters = false },
            onOpenLetter = onOpenLetter,
            onCreateLetter = onCreateLetter,
        )
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
    forwardMany?.let { messages ->
        ForwardDialog(
            conversations = state.conversations.filter { it.id != state.conversationId },
            onSelect = { destinationConversationId ->
                onForwardMany(messages, destinationConversationId)
                forwardMany = null
                selectedIds = emptySet()
            },
            onDismiss = { forwardMany = null },
        )
    }
    confirmDeleteMany?.let { messages ->
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = { confirmDeleteMany = null },
            title = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.selection_delete_title, messages.size, messages.size)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (messages.isNotEmpty()) onDeleteMany(messages)
                    confirmDeleteMany = null
                    selectedIds = emptySet()
                }) { Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDeleteMany = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    translateFor?.let { message ->
        TranslateDialog(message = message, translate = onTranslate, onDismiss = { translateFor = null })
    }
    editHistoryFor?.let { message ->
        EditHistoryDialog(message = message, load = onLoadEditHistory, onDismiss = { editHistoryFor = null })
    }
    seenByFor?.let { message ->
        SeenByDialog(message = message, load = onLoadSeenBy, onDismiss = { seenByFor = null })
    }
    reactorsFor?.let { message ->
        // Re-resolve from state so the sheet follows live changes; close it if the message or all reactions are gone.
        val live = state.messages.firstOrNull { it.clientMsgId == message.clientMsgId }
        if (live == null || live.reactions.isEmpty()) {
            reactorsFor = null
        } else {
            val me = stringResource(R.string.reaction_you)
            val unknown = stringResource(R.string.reaction_unknown_user)
            val otherName = state.headerTitle
            val reactors = remember(live.reactions, selfUserId, state.memberDisplayNames, otherName) {
                buildReactors(
                    reactions = live.reactions,
                    selfUserId = selfUserId,
                    nameOf = { id -> state.memberDisplayNames[id] ?: if (!state.isGroupConversation && id == state.otherUserId) otherName else null },
                    selfName = me,
                    unknownName = unknown,
                )
            }
            ReactionDetailsSheet(
                reactors = reactors,
                onRemoveMine = {
                    live.reactions.firstOrNull { it.reactedByMe }?.let { onReact(live, it.emoji) }
                },
                onDismiss = { reactorsFor = null },
            )
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateChip(epochMillis: Long) {
    val header = remember(epochMillis) { ChatDateFormatter.header(epochMillis) }
    val label = when (header.day) {
        LastSeenFormatter.Day.TODAY -> stringResource(R.string.date_today)
        LastSeenFormatter.Day.YESTERDAY -> stringResource(R.string.date_yesterday)
        LastSeenFormatter.Day.OTHER -> header.text
    }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .shadow(2.dp, RoundedCornerShape(50), clip = false)
                .background(MenchBrand.glassFill(), RoundedCornerShape(50))
                .border(0.5.dp, MenchBrand.hairline(), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun UnreadDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
        Text(
            text = stringResource(R.string.chat_unread_divider),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 3.dp),
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    senderLabel: String?,
    showTail: Boolean = true,
    topSpacing: androidx.compose.ui.unit.Dp = 4.dp,
    repliedMessage: Message?,
    playbackState: PlaybackState,
    onImageClick: (MessageAttachment) -> Unit,
    onVideoClick: (MessageAttachment) -> Unit,
    onTogglePlayback: (sourceId: String, localPath: String?, remoteUrl: String?) -> Unit,
    onStartEdit: (Message) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onReact: (Message, String) -> Unit,
    onForwardRequested: (Message) -> Unit,
    onReplyRequested: (Message) -> Unit,
    onPinRequested: (Message) -> Unit = {},
    onRetryFailed: (Message) -> Unit = {},
    onDiscardFailed: (Message) -> Unit = {},
    onCancelUpload: (Message) -> Unit = {},
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEnterSelection: (Message) -> Unit = {},
    onDoubleTapReact: (Message) -> Unit = {},
    onShowReactors: (Message) -> Unit = {},
    onJumpToMessage: (Message) -> Unit = {},
    isGroupChat: Boolean = false,
    onShowSeenBy: (Message) -> Unit = {},
    onShowTranslate: (Message) -> Unit = {},
    onShowEditHistory: (Message) -> Unit = {},
    albumItems: List<Message>? = null,
) {
    // Phase 2A: 1-3 emoji and nothing else -> big emoji, no bubble.
    val emojiCount = remember(message.body) { EmojiText.bigEmojiCount(message.body) }
    val bigEmoji = message.kind == DomainMessageKind.TEXT && message.deletedAtEpochMillis == null &&
        message.replyToMessageId == null && message.forwardedFromMessageId == null && emojiCount > 0
    // Phase 2A: sticker / GIF messages are TEXT messages carrying a small marker (see RichBody).
    val rich = remember(message.body, message.kind) {
        if (message.kind == DomainMessageKind.TEXT) RichBody.parse(message.body) else RichBody.Plain
    }
    val isRoundVideo = message.kind == DomainMessageKind.VIDEO && message.deletedAtEpochMillis == null &&
        RoundVideo.isRoundFilename(message.attachment?.originalFilename)
    val richStandalone = ((rich is RichBody.Sticker || rich is RichBody.Gif) || isRoundVideo) && message.deletedAtEpochMillis == null &&
        message.replyToMessageId == null && message.forwardedFromMessageId == null
    val alignment = if (message.isOwn) Alignment.CenterEnd else Alignment.CenterStart
    val primary = MaterialTheme.colorScheme.primary
    val darkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Received bubbles are crisp white cards (light) or a lifted plum surface (dark),
    // so they read clearly on any wallpaper; own bubbles use the brand gradient.
    val bubbleColor = if (message.isOwn) primary else if (darkUi) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest
    // Phase 3: per-chat bubble skin (classic = the drawing below; glass / neon / paper replace shape, fill and text colour).
    val bubbleSkin = rememberBubbleSkin(message.conversationId)
    val skinned = if (bigEmoji || richStandalone) null else skinStyle(bubbleSkin, message.isOwn, showTail, primary, darkUi)
    val contentColor = skinned?.contentColor ?: if (message.isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    // The corner nearest the sender is tight (a "tail") only on the last bubble of a group;
    // topStart/topEnd/bottomEnd/bottomStart are layout-direction aware, so this mirrors in RTL.
    val tail = if (showTail) 5.dp else 20.dp
    val bubbleShape = skinned?.shape ?: if (message.isOwn) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = tail, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = tail)
    }
    val bubbleBackground = if (bigEmoji || richStandalone) {
        Modifier
    } else if (skinned != null) {
        skinned.modifier
    } else if (message.isOwn) {
        Modifier
            .shadow(4.dp, bubbleShape, clip = false, ambientColor = primary.copy(alpha = 0.25f), spotColor = primary.copy(alpha = 0.45f))
            .background(MenchBrand.bubbleBrush(), bubbleShape)
    } else {
        Modifier
            .shadow(1.5.dp, bubbleShape, clip = false)
            .background(bubbleColor, bubbleShape)
            .border(0.5.dp, MenchBrand.hairline(), bubbleShape)
    }
    val isDeleted = message.deletedAtEpochMillis != null
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // A send the server never accepted (and that gave up retrying) is actionable instead of a normal message.
    val isFailedOwn = message.isOwn && message.state == DomainMessageState.FAILED && message.serverId == null

    // TalkBack (Phase 4A): one focus stop per bubble, announced as "sender, content, time, status",
    // with the long-press actions exposed as custom actions (long-press is not reachable by TalkBack).
    val a11yDescription = messageA11yDescription(message, senderLabel)
    val a11yReplyLabel = stringResource(R.string.chat_reply)
    val a11yReactLabel = stringResource(R.string.a11y_action_react_heart)
    val a11yDeleteLabel = stringResource(R.string.chat_delete)

    if (confirmDiscard) {
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.message_delete_failed)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDiscard = false
                    onDiscardFailed(message)
                }) { Text(stringResource(R.string.message_delete_failed)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Long-press-and-slide reactions (Phase 2A): long press shows a bar above the bubble; sliding the finger over an
    // emoji and releasing sends it; releasing anywhere else opens the normal action sheet.
    var slideBar by remember { mutableStateOf(false) }
    var slideTracking by remember { mutableStateOf(false) }
    var slideIndex by remember { mutableStateOf(-1) }
    var bubbleWindowX by remember { mutableStateOf(0f) }
    var bubbleWindowY by remember { mutableStateOf(0f) }
    var bubbleWidthPx by remember { mutableStateOf(0) }
    val slideDensity = androidx.compose.ui.platform.LocalDensity.current
    val slideWindowWidthPx = with(slideDensity) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    // Touch exploration (TalkBack) triggers onLongClick without a finger on the glass: fall back to the sheet.
    LaunchedEffect(slideBar) {
        if (slideBar) {
            kotlinx.coroutines.delay(180)
            if (!slideTracking) { slideBar = false; menuExpanded = true }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().padding(top = topSpacing, bottom = 1.dp), contentAlignment = alignment) {
        Box {
            if (slideBar) SlideReactionBar(selectedIndex = slideIndex)
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .onGloballyPositioned { c ->
                            val p = c.positionInWindow()
                            bubbleWindowX = p.x; bubbleWindowY = p.y; bubbleWidthPx = c.size.width
                        }
                        .pointerInput(message.clientMsgId, selectionMode, isDeleted, isFailedOwn) {
                            if (selectionMode || isDeleted || isFailedOwn || message.serverId == null) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                slideTracking = true
                                slideBar = true
                                var picked = -1
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val cell = slideCellPx(this)
                                    val barWidth = QUICK_REACTIONS.size * cell + slideBarPaddingPx(this) * 2
                                    val barLeft = (bubbleWindowX + bubbleWidthPx / 2f - barWidth / 2f).coerceIn(0f, (slideWindowWidthPx - barWidth).coerceAtLeast(0f))
                                    val localX = bubbleWindowX + change.position.x - barLeft - slideBarPaddingPx(this)
                                    val y = change.position.y
                                    val barH = cell + slideBarPaddingPx(this) * 2
                                    picked = if (y in (-barH * 1.8f)..(barH * 0.25f) && localX >= 0f && localX < QUICK_REACTIONS.size * cell) (localX / cell).toInt() else -1
                                    slideIndex = picked
                                    if (!change.pressed) break
                                }
                                slideTracking = false
                                slideBar = false
                                slideIndex = -1
                                if (picked in QUICK_REACTIONS.indices) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onReact(message, QUICK_REACTIONS[picked])
                                } else {
                                    menuExpanded = true
                                }
                            }
                        }
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) onToggleSelect() else if (isFailedOwn) onRetryFailed(message)
                            },
                            onDoubleClick = {
                                if (!selectionMode && !isDeleted && message.serverId != null) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onDoubleTapReact(message)
                                }
                            },
                            // A deleted message has nothing left worth acting
                            // on (no re-editing a deleted message, no
                            // reacting to it) — see MessagesRepository.react's
                            // matching server-side deleted_at check.
                            onLongClick = {
                                if (selectionMode) {
                                    onToggleSelect()
                                } else if (isFailedOwn) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    confirmDiscard = true
                                } else if (!isDeleted) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    // Shows the slide-to-react bar; the pointer tracker above finishes the gesture (or falls back to the sheet).
                                    slideBar = true
                                }
                            },
                        )
                        .then(bubbleBackground)
                        .semantics(mergeDescendants = true) {
                            contentDescription = a11yDescription
                            if (!isDeleted) {
                                customActions = buildList {
                                    add(CustomAccessibilityAction(a11yReplyLabel) { onReplyRequested(message); true })
                                    if (message.serverId != null) {
                                        add(CustomAccessibilityAction(a11yReactLabel) { onReact(message, "\u2764\uFE0F"); true })
                                        if (message.isOwn) {
                                            add(CustomAccessibilityAction(a11yDeleteLabel) { onDeleteMessage(message); true })
                                        }
                                    }
                                }
                            }
                        }
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
                        if (repliedMessage != null) {
                            ReplyQuotePreview(repliedMessage, tintOnPrimary = message.isOwn, onClick = { onJumpToMessage(repliedMessage) })
                        }
                        if (message.forwardedFromMessageId != null) {
                            Text(
                                text = stringResource(R.string.chat_forwarded_label),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        when (message.kind) {
                            DomainMessageKind.IMAGE -> message.attachment?.let { attachment ->
                                if (albumItems != null) AlbumGrid(albumItems, onImageClick)
                                else if (!message.isOwn && attachment.isViewOnce()) ViewOnceTile(message.clientMsgId, attachment)
                                else ImageAttachmentContent(message.conversationId, attachment, onClick = { onImageClick(attachment) })
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
                                if (isRoundVideo) RoundVideoBubble(attachment)
                                else VideoAttachmentContent(message.conversationId, attachment, onClick = { onVideoClick(attachment) })
                            }
                            DomainMessageKind.TEXT -> Unit
                        }

                        val sharedEvent = com.omidgame.mench.feature.product.EventCodec.decode(message.body)
                        if (sharedEvent != null) {
                            com.omidgame.mench.feature.product.EventCard(sharedEvent)
                        } else if (!message.body.isNullOrBlank()) {
                            if (rich is RichBody.Sticker) {
                                StickerView(rich.spec, 150.dp)
                            } else if (rich is RichBody.Gif) {
                                GifBubbleImage(rich.id, rich.widthPx, rich.heightPx)
                            } else if (rich is RichBody.Poll) {
                                PollBubbleContent(rich.pollId)
                            } else if (rich is RichBody.Location) {
                                LocationBubbleContent(rich.latitude, rich.longitude, rich.liveUntilEpochMillis)
                            } else if (rich is RichBody.Surprise) {
                                SurpriseBubbleContent(message.clientMsgId, rich.text, message.isOwn)
                            } else if (rich is RichBody.Contact) {
                                ContactBubbleContent(rich.name, rich.phone)
                            } else if (bigEmoji) {
                                Text(
                                    text = message.body,
                                    fontSize = when (emojiCount) { 1 -> 64.sp; 2 -> 52.sp; else -> 42.sp },
                                    lineHeight = when (emojiCount) { 1 -> 76.sp; 2 -> 62.sp; else -> 50.sp },
                                )
                            } else {
                            if (message.kind == DomainMessageKind.TEXT && message.deletedAtEpochMillis == null) {
                                RichMessageText(message.body)
                            } else {
                                Text(message.body)
                            }
                            if (message.kind == DomainMessageKind.TEXT && message.deletedAtEpochMillis == null) {
                                firstUrl(message.body)?.let { LinkPreviewCard(it) }
                            }
                            }
                        }

                        if (message.reactions.isNotEmpty()) {
                            ReactionsRow(
                                reactions = message.reactions,
                                onReactionClick = { onReact(message, it) },
                                onReactionLongClick = { onShowReactors(message) },
                            )
                        }

                        // Footer: edited label, time and (own messages) the receipt, tucked to the end.
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (message.editedAtEpochMillis != null && !(rich is RichBody.Location && rich.liveUntilEpochMillis != null)) {
                                Text(
                                    text = stringResource(R.string.chat_message_edited),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalContentColor.current.copy(alpha = 0.7f),
                                    // Phase 2: tapping the label opens the earlier versions (needs the server id).
                                    modifier = if (message.serverId != null) Modifier.clickable { onShowEditHistory(message) } else Modifier,
                                )
                            }
                            Text(
                                text = remember(message.createdAtEpochMillis) { ChatDateFormatter.time(message.createdAtEpochMillis) },
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                            )
                            if (message.isOwn) {
                                // clock = sending, ✓ = sent, ✓✓ = delivered, tinted ✓✓ = read, ! = failed.
                                val readTint = if (darkUi) Color(0xFF7FD6FF) else Color(0xFFBFEFFF)
                                val (stateIcon, stateTint) = when (message.state) {
                                    DomainMessageState.PENDING, DomainMessageState.SENDING ->
                                        Icons.Filled.Schedule to LocalContentColor.current.copy(alpha = 0.7f)
                                    DomainMessageState.SENT -> Icons.Filled.Done to LocalContentColor.current.copy(alpha = 0.8f)
                                    DomainMessageState.DELIVERED -> Icons.Filled.DoneAll to LocalContentColor.current.copy(alpha = 0.8f)
                                    DomainMessageState.READ -> Icons.Filled.DoneAll to readTint
                                    DomainMessageState.FAILED -> Icons.Filled.ErrorOutline to Color(0xFFFFB4B4)
                                }
                                // Live upload progress for this one message only (null = not uploading).
                                val uploadFraction by remember(message.clientMsgId) {
                                    com.omidgame.mench.core.media.UploadProgress.progress
                                        .map { it[message.clientMsgId] }
                                        .distinctUntilChanged()
                                }.collectAsState(initial = null)
                                val fractionNow = uploadFraction
                                if (fractionNow != null && message.state != DomainMessageState.FAILED) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        progress = { fractionNow },
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable(onClickLabel = stringResource(R.string.message_cancel_upload)) { onCancelUpload(message) },
                                        strokeWidth = 2.dp,
                                        color = LocalContentColor.current,
                                    )
                                } else {
                                    Icon(
                                        imageVector = stateIcon,
                                        contentDescription = stringResource(stateLabelRes(message.state)),
                                        tint = stateTint,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                if (isFailedOwn) {
                                    Text(
                                        text = stringResource(R.string.message_retry),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFFB4B4),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (menuExpanded) {
                MessageActionSheet(
                    message = message,
                    onDismiss = { menuExpanded = false },
                    onReact = onReact,
                    onStartEdit = onStartEdit,
                    onDeleteMessage = onDeleteMessage,
                    onForwardRequested = onForwardRequested,
                    onReplyRequested = onReplyRequested,
                    onPinRequested = onPinRequested,
                    onSelectRequested = { onEnterSelection(message) },
                    onShowReactors = onShowReactors,
                    isGroupChat = isGroupChat,
                    onShowSeenBy = onShowSeenBy,
                    onShowTranslate = onShowTranslate,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: Message,
    onDismiss: () -> Unit,
    onReact: (Message, String) -> Unit,
    onStartEdit: (Message) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onForwardRequested: (Message) -> Unit,
    onReplyRequested: (Message) -> Unit,
    onPinRequested: (Message) -> Unit,
    onSelectRequested: () -> Unit = {},
    onShowReactors: (Message) -> Unit = {},
    isGroupChat: Boolean = false,
    onShowSeenBy: (Message) -> Unit = {},
    onShowTranslate: (Message) -> Unit = {},
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    @Composable
    fun ActionRow(label: String, destructive: Boolean = false, onClick: () -> Unit) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                    onDismiss()
                }
                .padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
    MenchSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QUICK_REACTIONS.forEach { emoji ->
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                onReact(message, emoji)
                                onDismiss()
                            }
                            .padding(6.dp),
                    )
                }
            }
            if (message.reactions.isNotEmpty()) {
                ActionRow(stringResource(R.string.reaction_details_action)) { onShowReactors(message) }
            }
            if (isGroupChat && message.isOwn && message.serverId != null) {
                ActionRow(stringResource(R.string.seen_by_action)) { onShowSeenBy(message) }
            }
            if (message.serverId != null) {
                ActionRow(stringResource(R.string.chat_reply)) { onReplyRequested(message) }
                ActionRow(stringResource(R.string.pin_message)) { onPinRequested(message) }
            }
            if (!message.body.isNullOrBlank() && RichBody.kindOf(message.body) == null) {
                ActionRow(stringResource(R.string.chat_copy)) {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(message.body))
                }
            }
            if (!message.body.isNullOrBlank() && RichBody.kindOf(message.body) == null) {
                ActionRow(stringResource(R.string.translate_action)) { onShowTranslate(message) }
            }
            if (message.kind == DomainMessageKind.TEXT && message.serverId != null) {
                ActionRow(stringResource(R.string.chat_forward)) { onForwardRequested(message) }
            }
            ActionRow(stringResource(R.string.chat_select)) { onSelectRequested() }
            if (message.isOwn && message.kind == DomainMessageKind.TEXT && message.serverId != null && RichBody.kindOf(message.body) == null) {
                ActionRow(stringResource(R.string.chat_edit)) { onStartEdit(message) }
            }
            if (message.isOwn && message.serverId != null) {
                ActionRow(stringResource(R.string.chat_delete), destructive = true) { onDeleteMessage(message) }
            }
            // Phase 1D: save to personal cloud, remind me, report.
            ProductMessageActions(message = message, onDone = onDismiss)
        }
    }
}

@Composable
private fun ReactionsRow(
    reactions: List<ReactionSummary>,
    onReactionClick: (String) -> Unit,
    onReactionLongClick: () -> Unit = {},
) {
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
                    .combinedClickable(onClick = { onReactionClick(reaction.emoji) }, onLongClick = onReactionLongClick)
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
private fun ImageAttachmentContent(conversationId: String, attachment: MessageAttachment, onClick: () -> Unit) {
    val gate = rememberMediaGate(conversationId, attachment, com.omidgame.mench.core.media.MediaKind.PHOTO)
    if (!gate.allowed) {
        MediaDownloadPlaceholder(attachment, onClick = gate.allow)
        return
    }
    // Prefer the local on-device copy when there is one (this device's own
    // outgoing image, before or regardless of upload completing) — falls
    // back to the server thumbnail URL for received images. Coil handles
    // both a local file path and an authenticated https URL through the
    // same ImageLoader (see core/di/ImageLoaderModule.kt).
    val model = attachment.localContentUri
        ?: attachment.id?.let { AttachmentUrls.thumbnail(it) }

    // Received image with a BlurHash: show the blurred preview in the final size until the thumbnail arrives.
    val blur = rememberBlurHashBitmap(attachment.blurHash.takeIf { attachment.localContentUri == null })
    val w = attachment.widthPx
    val h = attachment.heightPx
    if (blur != null && w != null && h != null && w > 0 && h > 0) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .androidx_aspectRatio((w.toFloat() / h.toFloat()).coerceIn(0.5f, 2f))
                .clickable(enabled = attachment.id != null, onClick = onClick),
        ) {
            androidx.compose.foundation.Image(
                bitmap = blur,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            AsyncImage(
                model = model,
                contentDescription = attachment.originalFilename,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        return
    }
    // The thumbnail's on-screen rectangle, handed to the viewer so it can grow out of (and shrink back into) it.
    var thumbBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    AsyncImage(
        model = model,
        contentDescription = attachment.originalFilename,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .onGloballyPositioned { thumbBounds = it.boundsInWindow() }
            .clickable(enabled = attachment.id != null) {
                ViewerOrigin.bounds = thumbBounds
                onClick()
            },
    )
}

/** Decodes a BlurHash off the main thread once per hash; null until ready or if there is no/invalid hash. */
@Composable
private fun rememberBlurHashBitmap(hash: String?): androidx.compose.ui.graphics.ImageBitmap? {
    var bitmap by remember(hash) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(hash) {
        bitmap = if (hash == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.omidgame.mench.core.media.BlurHashDecoder.decode(hash, 32, 32)?.asImageBitmap()
        }
    }
    return bitmap
}

private fun Modifier.androidx_aspectRatio(ratio: Float): Modifier = this.aspectRatio(ratio)

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
    // Phase 2A: true waveform, scrubbing, elapsed time and 1x/1.5x/2x live in VoiceBubbleContent.
    VoiceBubbleContent(
        clientMsgId = clientMsgId,
        attachment = attachment,
        isPlayingThis = isPlayingThis,
        onTogglePlayback = onTogglePlayback,
    )
}

/** Where the tapped thumbnail sits on screen, so the viewer can grow out of it and shrink back into it (Phase 2A). */
internal object ViewerOrigin {
    @Volatile var bounds: androidx.compose.ui.geometry.Rect? = null
}

@Composable
private fun ImageViewerDialog(attachment: MessageAttachment, onDismiss: () -> Unit) {
    val model = attachment.localContentUri
        ?: attachment.id?.let { AttachmentUrls.content(it) }
    CinematicImageViewer(
        model = model,
        contentDescription = stringResource(R.string.chat_view_image),
        onDismiss = onDismiss,
    )
}

@Composable
private fun VideoAttachmentContent(conversationId: String, attachment: MessageAttachment, onClick: () -> Unit) {
    val gate = rememberMediaGate(conversationId, attachment, com.omidgame.mench.core.media.MediaKind.VIDEO)
    if (!gate.allowed) {
        MediaDownloadPlaceholder(attachment, onClick = gate.allow)
        return
    }
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
            } else if (attachment.id != null) {
                // Phase 2A: stream a received video. VideoView.setVideoURI(uri, headers) carries the bearer token.
                val authHeaders = LocalMediaAuth.current
                var headers by remember { mutableStateOf<Map<String, String>?>(null) }
                LaunchedEffect(attachment.id) { headers = authHeaders() }
                headers?.let { resolved ->
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        factory = { context ->
                            VideoView(context).apply {
                                setVideoURI(Uri.parse(AttachmentUrls.content(attachment.id)), resolved)
                                setOnPreparedListener { it.start() }
                            }
                        },
                        onRelease = { it.stopPlayback() },
                    )
                }
            } else {
                val model = attachment.localThumbnailUri
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

@Composable
private fun ReplyQuotePreview(repliedMessage: Message, tintOnPrimary: Boolean, onClick: () -> Unit = {}) {
    // On an own (primary-colored) bubble, onPrimary keeps the quote
    // readable against that background; on a received (surfaceVariant)
    // bubble, the default primary tint gives it the same "accent bar"
    // look most messengers use for a reply quote.
    val barColor = if (tintOnPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = 4.dp)
            .background(barColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(barColor, RoundedCornerShape(2.dp)),
        )
        Text(
            text = when {
                repliedMessage.deletedAtEpochMillis != null -> stringResource(R.string.chat_message_deleted)
                RichBody.kindOf(repliedMessage.body) != null -> stringResource(richKindLabel(RichBody.kindOf(repliedMessage.body)!!))
                !repliedMessage.body.isNullOrBlank() -> RichText.plainText(repliedMessage.body)
                else -> stringResource(R.string.chat_replying_to_media)
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private fun stateLabelRes(state: DomainMessageState): Int = when (state) {
    DomainMessageState.PENDING, DomainMessageState.SENDING -> R.string.chat_message_state_sending
    DomainMessageState.SENT -> R.string.chat_message_state_sent
    DomainMessageState.DELIVERED -> R.string.chat_message_state_delivered
    DomainMessageState.READ -> R.string.chat_message_state_read
    DomainMessageState.FAILED -> R.string.chat_message_state_failed
}

@Composable
private fun Composer(
    conversationId: String,
    mentionCandidates: List<String> = emptyList(),
    isRecording: Boolean,
    editingMessage: Message?,
    replyingToMessage: Message?,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
    onSendWithEffect: (String, MessageEffect) -> Unit,
    onSendAttachment: (contentUri: String, mimeType: String, originalFilename: String) -> Unit,
    onSendPhotos: (List<PhotoOutgoing>) -> Unit = {},
    onTogether: (() -> Unit)? = null,
    onStartRecording: () -> Boolean,
    onStopRecordingAndSend: () -> Unit,
    onCancelRecording: () -> Unit,
    onCancelEdit: () -> Unit,
    onSubmitEdit: (String) -> Unit,
    recorderLive: com.omidgame.mench.core.media.RecorderLive = com.omidgame.mench.core.media.RecorderLive(),
    onCreatePoll: (String, List<String>, Boolean) -> Unit = { _, _, _ -> },
    onSendRoundVideo: (java.io.File) -> Unit = {},
    scheduled: List<com.omidgame.mench.feature.chat.domain.ScheduledMessage> = emptyList(),
    onSendSilently: (String) -> Unit = {},
    onScheduleMessage: (String, Long, Boolean) -> Unit = { _, _, _ -> },
    onCancelScheduled: (com.omidgame.mench.feature.chat.domain.ScheduledMessage) -> Unit = {},
) {
    val draftContext = LocalContext.current
    // Restores the unsent text of THIS conversation, and saves it as you type
    // (never while editing an existing message, whose text is not a draft).
    var text by remember(conversationId) { mutableStateOf(DraftStore.get(draftContext, conversationId)) }
    val composerSettings = rememberChatSettings()
    LaunchedEffect(text, editingMessage) {
        if (editingMessage == null) DraftStore.set(draftContext, conversationId, text)
    }
    // Draft written on another device (only when Settings > Sync is on): adopt it if this composer is still empty.
    LaunchedEffect(conversationId) {
        DraftSync.pull(draftContext)
        if (text.isBlank()) text = DraftStore.get(draftContext, conversationId)
    }
    var showEmoji by remember { mutableStateOf(false) }
    var showDrawing by remember { mutableStateOf(false) }
    var showEffectPicker by remember { mutableStateOf(false) }
    if (showEffectPicker) {
        EffectPickerDialog(
            onDismiss = { showEffectPicker = false },
            onPick = { effect ->
                showEffectPicker = false
                if (text.isNotBlank()) {
                    onSendWithEffect(text, effect)
                    text = ""
                }
            },
        )
    }
    if (showDrawing) {
        DrawingDialog(onDismiss = { showDrawing = false }, onSend = onSendAttachment)
    }
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

    // Phase 2A: gallery multi-select (up to 10) -> photo composer (edit, caption, original quality).
    var composerUris by remember { mutableStateOf<List<android.net.Uri>?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Albums.MAX)) { uris ->
        if (uris.isNotEmpty()) composerUris = uris
    }
    composerUris?.let { picked ->
        PhotoComposerDialog(uris = picked, onDismiss = { composerUris = null }, onSend = onSendPhotos)
    }
    // Phase 2A: attach grid (file / poll / location), poll form and the location flow.
    var showSendOptions by remember { mutableStateOf(false) }
    var showScheduledList by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showRoundVideo by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var pendingLocation by remember { mutableStateOf<LatLon?>(null) }
    val locationScope = androidx.compose.runtime.rememberCoroutineScope()
    fun fetchLocation() {
        locationScope.launch {
            android.widget.Toast.makeText(context, R.string.location_locating, android.widget.Toast.LENGTH_SHORT).show()
            val found = LocationFetcher.current(context)
            if (found != null) pendingLocation = found
            else android.widget.Toast.makeText(context, R.string.location_unavailable, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) fetchLocation()
        else android.widget.Toast.makeText(context, R.string.location_permission_denied, android.widget.Toast.LENGTH_LONG).show()
    }
    val contactPickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            readPickedContact(context, uri)?.let { (name, phone) ->
                val body = RichBody.encodeContact(name, phone)
                if (body != null) onSend(body)
                else android.widget.Toast.makeText(context, R.string.contact_invalid, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    fun startLocationShare() {
        if (LocationFetcher.hasPermission(context)) fetchLocation()
        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // Phase 2A: hold the mic to record (release = send, slide toward the start edge = cancel,
    // slide up or tap = lock hands-free). `locked` shows Cancel / Stop instead of needing a held finger.
    var recordingLocked by remember { mutableStateOf(false) }
    var cancelFraction by remember { mutableStateOf(0f) }
    var lockFraction by remember { mutableStateOf(0f) }
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            recordingLocked = false
            cancelFraction = 0f
            lockFraction = 0f
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // The finger is long gone after the system dialog, so continue hands-free.
            if (onStartRecording()) recordingLocked = true
        } else {
            permissionJustDenied = true
        }
    }

    /** True when recording started right now; false while the permission prompt is up or the mic is unavailable. */
    fun startRecordingWithPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) {
            onStartRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            false
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
        } else if (replyingToMessage != null) {
            // else if, not a second independent if: editing an existing
            // message and replying to a different one at the same time
            // has no sensible UI, and ChatViewModel.onReplyToMessage never
            // needs to check this — showing at most one banner here is
            // simpler than preventing the state combination upstream.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_replying_to_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = replyingToMessage.body?.take(80) ?: stringResource(R.string.chat_replying_to_media),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onCancelReply) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel_reply))
                }
            }
        }
        // @mention autocomplete (Phase 2A): typing "@" + letters at the end of the text suggests group members.
        val mentionQuery = remember(text) { Regex("(?:^|\\s)@([\\p{L}\\p{N}_.]{0,32})$").find(text)?.groupValues?.get(1) }
        if (mentionQuery != null && mentionCandidates.isNotEmpty()) {
            val matches = mentionCandidates.filter { it.replace(' ', '_').contains(mentionQuery, ignoreCase = true) }.take(5)
            if (matches.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    matches.forEach { name ->
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                // Names with spaces become one token (Sara_Ahmadi) because RichText mentions have no spaces.
                                val token = "@" + name.trim().replace(' ', '_') + " "
                                text = text.substring(0, text.length - mentionQuery.length - 1) + token
                            },
                            label = { Text(name, maxLines = 1) },
                        )
                    }
                }
            }
        }
        if (showAttachMenu) {
            AttachMenuSheet(
                onFile = { pickLauncher.launch("*/*") },
                onTogether = onTogether,
                onGallery = {
                    galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPoll = { showPollDialog = true },
                onLocation = { startLocationShare() },
                onContact = { contactPickLauncher.launch(contactPickIntent()) },
                onDismiss = { showAttachMenu = false },
            )
        }
        if (scheduled.isNotEmpty()) {
            androidx.compose.material3.TextButton(onClick = { showScheduledList = true }, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.scheduled_count, scheduled.size, scheduled.size))
            }
        }
        if (showScheduledList) {
            ScheduledMessagesSheet(messages = scheduled, onCancel = onCancelScheduled, onDismiss = { showScheduledList = false })
        }
        if (showSendOptions) {
            SendOptionsSheet(
                onSilent = {
                    onSendSilently(text)
                    text = ""
                },
                onScheduleAt = { at, surprise ->
                    onScheduleMessage(if (surprise) (RichBody.encodeSurprise(text) ?: text) else text, at, false)
                    text = ""
                    showSendOptions = false
                },
                onDismiss = { showSendOptions = false },
                // Full-screen effect for this message; only offered when there is text to send.
                onEffect = if (text.isNotBlank()) ({ showEffectPicker = true }) else null,
            )
        }
        if (showRoundVideo) {
            RoundVideoRecorderDialog(
                onRecorded = { file ->
                    showRoundVideo = false
                    onSendRoundVideo(file)
                },
                onDismiss = { showRoundVideo = false },
            )
        }
        if (showPollDialog) {
            PollCreateDialog(
                onCreate = { question, options, multiple ->
                    showPollDialog = false
                    onCreatePoll(question, options, multiple)
                },
                onDismiss = { showPollDialog = false },
            )
        }
        pendingLocation?.let { found ->
            LocationConfirmDialog(
                location = found,
                onSend = {
                    RichBody.encodeLocation(found.latitude, found.longitude)?.let(onSend)
                    pendingLocation = null
                },
                onDismiss = { pendingLocation = null },
                onShareLive = { durationMs ->
                    pendingLocation = null
                    if (com.omidgame.mench.feature.chat.service.LiveLocationService.running) {
                        android.widget.Toast.makeText(context, R.string.live_location_busy, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        com.omidgame.mench.feature.chat.service.LiveLocationService.start(context, conversationId, durationMs, found)
                    }
                },
            )
        }
        if (showEmoji && !isRecording) {
            MediaPanel(
                onEmoji = { text += it },
                onRich = { body ->
                    // Stickers / GIFs are sent immediately (same path and reply handling as a typed message).
                    onSend(body)
                    showEmoji = false
                },
            )
        }
        val composerDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val composerShape = RoundedCornerShape(28.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .shadow(8.dp, composerShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                .background(if (composerDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest, composerShape)
                .border(0.5.dp, MenchBrand.hairline(), composerShape)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showDrawing = true }, enabled = !isRecording && editingMessage == null) {
                Icon(Icons.Filled.Brush, contentDescription = stringResource(R.string.drawing_title))
            }
            IconButton(onClick = { showEmoji = !showEmoji }, enabled = !isRecording) {
                Icon(
                    Icons.Filled.EmojiEmotions,
                    contentDescription = stringResource(R.string.chat_emoji),
                    tint = if (showEmoji) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            IconButton(
                onClick = { showAttachMenu = true },
                enabled = !isRecording && editingMessage == null,
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.chat_attach))
            }
            if (isRecording) {
                RecordingBar(
                    live = recorderLive,
                    locked = recordingLocked,
                    cancelFraction = cancelFraction,
                    onCancel = {
                        recordingLocked = false
                        onCancelRecording()
                    },
                    modifier = Modifier.weight(1f),
                )
            } else OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    // Settings Phase 2: with "Enter sends", a hardware Enter sends and Shift+Enter inserts a new line.
                    if (composerSettings.enterSends && event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter) && !event.isShiftPressed &&
                        text.isNotBlank() && editingMessage == null
                    ) {
                        onSend(text)
                        text = ""
                        true
                    } else {
                        false
                    }
                },
                enabled = !isRecording,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = if (composerSettings.autoCapitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                    autoCorrectEnabled = composerSettings.spellCheck,
                    imeAction = if (composerSettings.enterSends) ImeAction.Send else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank() && editingMessage == null) {
                        onSend(text)
                        text = ""
                    }
                }),
                shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                ),
                placeholder = { Text(stringResource(R.string.chat_composer_hint)) },
            )
            if (!isRecording && editingMessage == null && text.isBlank()) {
                IconButton(onClick = { showRoundVideo = true }) {
                    Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.round_video_record))
                }
            }
            if (isRecording && recordingLocked) {
                IconButton(onClick = {
                    recordingLocked = false
                    onStopRecordingAndSend()
                }) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.chat_stop_recording))
                }
            } else {
                val micLabel = stringResource(R.string.chat_record_voice)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = micLabel
                            // TalkBack / switch access: activate = start recording hands-free.
                            onClick(label = micLabel) {
                                if (startRecordingWithPermission()) recordingLocked = true
                                true
                            }
                        }
                        .holdToRecord(
                            enabled = editingMessage == null,
                            onStart = ::startRecordingWithPermission,
                            onLocked = { recordingLocked = true },
                            onFinish = { send ->
                                recordingLocked = false
                                if (send) onStopRecordingAndSend() else onCancelRecording()
                            },
                            onDrag = { cancel, lock ->
                                cancelFraction = cancel
                                lockFraction = lock
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (isRecording) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                    if (isRecording && !recordingLocked) {
                        LockHint(lockFraction, Modifier.align(Alignment.TopCenter).offset(y = (-64).dp))
                    }
                }
            }
            com.omidgame.mench.core.ui.components.AnimatedSendButton(
                canSend = text.isNotBlank() && !isRecording,
                isEditing = editingMessage != null,
                onLongClick = if (editingMessage == null) ({ showSendOptions = true }) else null,
                contentDescription = stringResource(
                    if (editingMessage != null) R.string.chat_save_edit else R.string.chat_send,
                ),
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
            )
        }
    }
}

/** Only plain text can be forwarded today (see MessagesService.forward on the backend). */
private fun Message.canForward(): Boolean =
    kind == DomainMessageKind.TEXT && serverId != null && deletedAtEpochMillis == null

/** Phase 2A — contextual top bar while messages are selected: count, copy, pin (one message), forward, delete (own). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selected: List<Message>,
    onClose: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onCopied: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val copyable = selected.filter { !it.body.isNullOrBlank() && it.deletedAtEpochMillis == null }
    val canForward = selected.isNotEmpty() && selected.all { it.canForward() }
    val canDelete = selected.isNotEmpty() && selected.all { it.isOwn && it.serverId != null && it.deletedAtEpochMillis == null }
    val canPin = selected.size == 1 && selected[0].serverId != null && selected[0].deletedAtEpochMillis == null
    TopAppBar(
        modifier = Modifier.background(MenchBrand.heroBrush()),
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        title = {
            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.selection_count, selected.size, selected.size))
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.selection_cancel))
            }
        },
        actions = {
            if (copyable.isNotEmpty()) {
                IconButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(copyable.joinToString("\n") { it.body.orEmpty() }))
                    onCopied()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.chat_copy))
                }
            }
            if (canPin) {
                IconButton(onClick = onPin) {
                    Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.pin_message))
                }
            }
            if (canForward) {
                IconButton(onClick = onForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.chat_forward))
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.chat_delete))
                }
            }
        },
    )
}

/** Phase 2A: "seen by" list for one of my group messages, loaded on open. */
@Composable
private fun SeenByDialog(
    message: Message,
    load: suspend (Message) -> List<com.omidgame.mench.feature.chat.domain.SeenBy>?,
    onDismiss: () -> Unit,
) {
    var result by remember(message.clientMsgId) { mutableStateOf<List<com.omidgame.mench.feature.chat.domain.SeenBy>?>(null) }
    var failed by remember(message.clientMsgId) { mutableStateOf(false) }
    LaunchedEffect(message.clientMsgId) {
        val loaded = load(message)
        if (loaded == null) failed = true else result = loaded
    }
    com.omidgame.mench.core.ui.components.MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.seen_by_title)) },
        text = {
            val list = result
            when {
                failed -> Text(stringResource(R.string.seen_by_error))
                list == null -> androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                list.isEmpty() -> Text(stringResource(R.string.seen_by_empty))
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { who ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(who.name, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(
                                LastSeenFormatter.format(who.readAtEpochMillis).time,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.seen_by_close)) } },
    )
}

/** Phase 2: earlier versions of an edited message, loaded on open (newest edit first). */
@Composable
private fun EditHistoryDialog(
    message: Message,
    load: suspend (Message) -> List<com.omidgame.mench.feature.chat.domain.EditVersion>?,
    onDismiss: () -> Unit,
) {
    var result by remember(message.clientMsgId) { mutableStateOf<List<com.omidgame.mench.feature.chat.domain.EditVersion>?>(null) }
    var failed by remember(message.clientMsgId) { mutableStateOf(false) }
    LaunchedEffect(message.clientMsgId) {
        val loaded = load(message)
        if (loaded == null) failed = true else result = loaded
    }
    com.omidgame.mench.core.ui.components.MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_history_title)) },
        text = {
            val list = result
            when {
                failed -> Text(stringResource(R.string.edit_history_error))
                list == null -> androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                list.isEmpty() -> Text(stringResource(R.string.edit_history_empty))
                else -> Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.edit_history_current), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(message.body.orEmpty())
                    list.asReversed().forEach { version ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                LastSeenFormatter.format(version.replacedAtEpochMillis).let { it.date + "  " + it.time },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(version.body)
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.seen_by_close)) } },
    )
}

/** Phase 2A: translation of one message, fetched through the backend when the dialog opens. */
@Composable
private fun TranslateDialog(message: Message, translate: suspend (Message) -> String?, onDismiss: () -> Unit) {
    var result by remember(message.clientMsgId) { mutableStateOf<String?>(null) }
    var failed by remember(message.clientMsgId) { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    LaunchedEffect(message.clientMsgId) {
        val text = translate(message)
        if (text == null) failed = true else result = text
    }
    com.omidgame.mench.core.ui.components.MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translate_title)) },
        text = {
            when {
                failed -> Text(stringResource(R.string.translate_unavailable))
                result == null -> androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else -> Text(result.orEmpty())
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.seen_by_close)) } },
        dismissButton = result?.let { text ->
            { androidx.compose.material3.TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }) { Text(stringResource(R.string.translate_copy)) } }
        },
    )
}

private fun slideCellPx(scope: androidx.compose.ui.unit.Density): Float = with(scope) { 36.dp.toPx() }
private fun slideBarPaddingPx(scope: androidx.compose.ui.unit.Density): Float = with(scope) { 8.dp.toPx() }

/** The floating emoji bar of the slide-to-react gesture. Geometry must match the hit-test in the bubble (cell 36 dp, padding 8 dp). */
@Composable
private fun SlideReactionBar(selectedIndex: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val provider = remember {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize,
            ): androidx.compose.ui.unit.IntOffset {
                val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
                return androidx.compose.ui.unit.IntOffset(x, y)
            }
        }
    }
    androidx.compose.ui.window.Popup(popupPositionProvider = provider) {
        Row(
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(8.dp),
        ) {
            QUICK_REACTIONS.forEachIndexed { index, emoji ->
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (index == selectedIndex) 1.5f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f),
                    label = "slideReactionScale",
                )
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                }
            }
        }
    }
}
