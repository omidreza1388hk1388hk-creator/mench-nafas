package com.omidgame.mench.feature.chat.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.filled.Settings
import com.omidgame.mench.core.ui.components.MenchMenu
import com.omidgame.mench.core.ui.components.MenchMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.UserAvatar
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.feature.chat.domain.Conversation
import com.omidgame.mench.feature.chat.domain.ConversationPreview
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    state: ConversationListUiState,
    onConversationClick: (String) -> Unit,
    onNewConversationClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onContactsClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onSetPinned: (conversationId: String, pinned: Boolean) -> Unit = { _, _ -> },
    onSetMuted: (conversationId: String, muted: Boolean) -> Unit = { _, _ -> },
    /** Phase 1C: block the other person of a direct chat. */
    onBlockUser: (userId: String) -> Unit = {},
    /** Phase 1D.8: report the other person of a direct chat. */
    onReportUser: (userId: String, category: String, details: String?) -> Unit = { _, _, _ -> },
    onSetArchived: (conversationId: String, archived: Boolean) -> Unit = { _, _ -> },
    onToggleArchivedView: () -> Unit = {},
    onExport: (Conversation) -> Unit = {},
    onRefresh: () -> Unit = {},
    // Phase 2A: folders, filters, mark read/unread
    onSelectFilter: (ChatFilter) -> Unit = {},
    onCreateFolder: (name: String, thenAddConversationId: String?) -> Unit = { _, _ -> },
    onRenameFolder: (folderId: String, name: String) -> Unit = { _, _ -> },
    onDeleteFolder: (folderId: String) -> Unit = {},
    onSetFolderMembership: (folderId: String, conversationId: String, member: Boolean) -> Unit = { _, _, _ -> },
    onMarkUnread: (conversationId: String, unread: Boolean) -> Unit = { _, _ -> },
    onMarkRead: (conversationId: String) -> Unit = {},
    onConversationOpened: (conversationId: String) -> Unit = {},
    onCallBack: (conversationId: String, callType: com.omidgame.mench.feature.calls.domain.CallType) -> Unit = { _, _ -> },
    joinState: ConversationListViewModel.JoinState = ConversationListViewModel.JoinState.Idle,
    onLookupInvite: (String) -> Unit = {},
    onConfirmJoin: () -> Unit = {},
    onJoinDismissed: () -> Unit = {},
) {
    var showJoin by remember { mutableStateOf(false) }
    // Folder dialogs: which one is open (state is hoisted here so any row can trigger them).
    var newFolderFor by remember { mutableStateOf<String?>(null) }       // null = closed; "" = plain new folder; id = new folder containing that chat
    var renameFolder by remember { mutableStateOf<ChatFolder?>(null) }
    var deleteFolder by remember { mutableStateOf<ChatFolder?>(null) }
    var pickerFor by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(refreshing) {
        if (refreshing) {
            kotlinx.coroutines.delay(900)
            refreshing = false
        }
    }
    var fabMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val showingArchived = (state as? ConversationListUiState.Content)?.showingArchived == true
                        if (!showingArchived) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(if (showingArchived) R.string.chat_archive else R.string.app_name),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                actions = {
                    var showCalls by remember { mutableStateOf(false) }
                    val missedViewModel: com.omidgame.mench.feature.calls.presentation.MissedCallsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val missedCount by missedViewModel.missed.collectAsStateWithLifecycle()
                    androidx.compose.runtime.LaunchedEffect(showCalls) { missedViewModel.refresh() }
                    if (showCalls) com.omidgame.mench.feature.calls.presentation.CallHistoryDialog(
                        onDismiss = { showCalls = false },
                        onCallBack = onCallBack,
                    )
                    IconButton(onClick = onOpenSaved) {
                        Icon(Icons.Filled.Bookmark, contentDescription = stringResource(R.string.saved_messages_open))
                    }
                    IconButton(onClick = { showCalls = true }) {
                        androidx.compose.material3.BadgedBox(
                            badge = { if (missedCount > 0) androidx.compose.material3.Badge { Text(missedCount.toString()) } },
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.calls_title))
                        }
                    }
                    val content = state as? ConversationListUiState.Content
                    if (content != null && (content.archivedCount > 0 || content.showingArchived)) {
                        IconButton(onClick = onToggleArchivedView) {
                            Icon(
                                if (content.showingArchived) Icons.Filled.Inbox else Icons.Filled.Archive,
                                contentDescription = stringResource(R.string.chat_archive),
                            )
                        }
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_hint))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { fabMenuExpanded = true },
                    shape = CircleShape,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier
                        .shadow(10.dp, CircleShape, clip = false, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        .background(MenchBrand.heroBrush(), CircleShape),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_new_conversation))
                }
                MenchMenu(expanded = fabMenuExpanded, onDismissRequest = { fabMenuExpanded = false }) {
                    MenchMenuItem(
                        text = { Text(stringResource(R.string.contacts_title)) },
                        onClick = {
                            fabMenuExpanded = false
                            onContactsClick()
                        },
                    )
                    MenchMenuItem(
                        text = { Text(stringResource(R.string.chat_new_direct_action)) },
                        onClick = {
                            fabMenuExpanded = false
                            onNewConversationClick()
                        },
                    )
                    MenchMenuItem(
                        text = { Text(stringResource(R.string.chat_new_group_action)) },
                        onClick = {
                            fabMenuExpanded = false
                            onNewGroupClick()
                        },
                    )
                    MenchMenuItem(
                        text = { Text(stringResource(R.string.join_group_action)) },
                        onClick = {
                            fabMenuExpanded = false
                            showJoin = true
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.foundation.layout.Column(Modifier.align(Alignment.TopCenter).zIndex(1f)) {
                com.omidgame.mench.core.ui.components.ConnectionBannerHost()
                com.omidgame.mench.feature.product.ProductBannersHost()
            }
            Column(modifier = Modifier.fillMaxSize()) {
            if (state is ConversationListUiState.Content && !state.showingArchived) {
                ChatFilterBar(
                    content = state,
                    onSelect = onSelectFilter,
                    onNewFolder = { newFolderFor = "" },
                    onRenameFolder = { renameFolder = it },
                    onDeleteFolder = { deleteFolder = it },
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is ConversationListUiState.Loading -> ConversationListSkeleton()
                is ConversationListUiState.Empty -> EmptyConversations()
                is ConversationListUiState.Error -> {
                    Text(
                        text = state.message,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is ConversationListUiState.Content -> {
                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = {
                            refreshing = true
                            onRefresh()
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (state.conversations.isEmpty()) {
                            item(key = "filter-empty") {
                                Text(
                                    text = stringResource(R.string.filter_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                )
                            }
                        }
                        items(state.conversations, key = { it.id }) { conversation ->
                            val realUnread = (state.previews[conversation.id]?.unreadCount ?: 0) > 0
                            val manual = conversation.id in state.manualUnread
                            Box(modifier = Modifier.animateItem()) {
                                SwipeableConversationRow(
                                    isUnread = realUnread || manual,
                                    isArchived = conversation.isArchived,
                                    onToggleRead = {
                                        if (realUnread || manual) onMarkRead(conversation.id) else onMarkUnread(conversation.id, true)
                                    },
                                    onToggleArchive = { onSetArchived(conversation.id, !conversation.isArchived) },
                                ) {
                                    ConversationRow(
                                        conversation = conversation,
                                        preview = state.previews[conversation.id],
                                        manuallyUnread = manual,
                                        isTyping = conversation.id in state.typingIds,
                                        pinLimitReached = state.conversations.count { it.isPinned } >= ConversationListViewModel.MAX_PINNED,
                                        onClick = {
                                            onConversationOpened(conversation.id)
                                            onConversationClick(conversation.id)
                                        },
                                        onSetPinned = { onSetPinned(conversation.id, it) },
                                        onSetMuted = { onSetMuted(conversation.id, it) },
                                        onBlockUser = { conversation.otherUserId?.let(onBlockUser) },
                                        onReportUser = { cat, details -> conversation.otherUserId?.let { onReportUser(it, cat, details) } },
                                        onSetArchived = { onSetArchived(conversation.id, it) },
                                        onExport = { onExport(conversation) },
                                        onToggleRead = {
                                            if (realUnread || manual) onMarkRead(conversation.id) else onMarkUnread(conversation.id, true)
                                        },
                                        onAddToFolder = { pickerFor = conversation.id },
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
            }
            }
        }
    }

    // ---- Phase 2A: join a group with a code ----
    if (showJoin || joinState !is ConversationListViewModel.JoinState.Idle) {
        JoinGroupDialog(
            state = joinState,
            onLookup = onLookupInvite,
            onConfirm = onConfirmJoin,
            onOpen = { id ->
                showJoin = false
                onJoinDismissed()
                onConversationClick(id)
            },
            onDismiss = {
                showJoin = false
                onJoinDismissed()
            },
        )
    }

    // ---- Phase 2A folder dialogs ----
    val contentState = state as? ConversationListUiState.Content
    newFolderFor?.let { forConversation ->
        FolderNameDialog(
            title = stringResource(R.string.folder_new),
            initial = "",
            onConfirm = { name ->
                onCreateFolder(name, forConversation.ifEmpty { null })
                newFolderFor = null
                pickerFor = null
            },
            onDismiss = { newFolderFor = null },
        )
    }
    renameFolder?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.folder_rename),
            initial = folder.name,
            onConfirm = { name ->
                onRenameFolder(folder.id, name)
                renameFolder = null
            },
            onDismiss = { renameFolder = null },
        )
    }
    deleteFolder?.let { folder ->
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text(stringResource(R.string.folder_delete_title, folder.name)) },
            text = { Text(stringResource(R.string.folder_delete_hint)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDeleteFolder(folder.id)
                    deleteFolder = null
                }) { Text(stringResource(R.string.folder_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteFolder = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    pickerFor?.let { conversationId ->
        if (newFolderFor == null) {
            FolderPickerDialog(
                folders = contentState?.folders.orEmpty(),
                conversationId = conversationId,
                onToggle = { folderId, member -> onSetFolderMembership(folderId, conversationId, member) },
                onNewFolder = { newFolderFor = conversationId },
                onDismiss = { pickerFor = null },
            )
        }
    }
}

/**
 * Phase 2A — swipe a chat row: toward the end = mark read / unread, toward the start = archive / unarchive.
 * The row always springs back (confirmValueChange returns false); the action is applied by the callbacks and the
 * list re-emits, which is what moves or restyles the row.
 */
@Composable
private fun SwipeableConversationRow(
    isUnread: Boolean,
    isArchived: Boolean,
    onToggleRead: () -> Unit,
    onToggleArchive: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggleRead()
                SwipeToDismissBoxValue.EndToStart -> onToggleArchive()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val direction = state.dismissDirection
            val readSide = direction == SwipeToDismissBoxValue.StartToEnd
            val color = if (readSide) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            val onColor = if (readSide) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (direction == SwipeToDismissBoxValue.Settled) Color.Transparent else color)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (readSide) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        imageVector = if (isUnread) Icons.Filled.MarkEmailRead else Icons.Filled.MarkEmailUnread,
                        contentDescription = stringResource(if (isUnread) R.string.chat_mark_read else R.string.chat_mark_unread),
                        tint = onColor,
                    )
                    SwipeToDismissBoxValue.EndToStart -> Icon(
                        imageVector = if (isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = stringResource(if (isArchived) R.string.chat_unarchive else R.string.chat_archive_action),
                        tint = onColor,
                    )
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        },
    ) { content() }
}

@Composable
private fun EmptyConversations() {
    val transition = rememberInfiniteTransition(label = "emptyHeart")
    val beat by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                1f at 0
                1.14f at 160
                1f at 320
                1.09f at 480
                1f at 700
                1f at 1800
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "emptyBeat",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .background(MenchBrand.glowBrush(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer { scaleX = beat; scaleY = beat }
                    .shadow(12.dp, CircleShape, clip = false, spotColor = MaterialTheme.colorScheme.primary)
                    .background(MenchBrand.heroBrush(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.empty_list_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_list_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shimmering placeholder rows shown while the first load is in flight. */
@Composable
private fun ConversationListSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val shine = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f).compositeOverWhite(base)
    val brush = Brush.linearGradient(
        colors = listOf(base, shine, base),
        start = androidx.compose.ui.geometry.Offset(x, 0f),
        end = androidx.compose.ui.geometry.Offset(x + 400f, 200f),
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(brush))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth(0.45f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
                    Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                }
            }
        }
    }
}

// Tiny helper so the shimmer highlight is never fully transparent on any theme.
private fun Color.compositeOverWhite(@Suppress("UNUSED_PARAMETER") base: Color): Color =
    androidx.compose.ui.graphics.lerp(base, if (base.luminance() < 0.5f) Color.White else Color.Black, 0.10f)

@Composable
private fun previewLine(preview: ConversationPreview?): String {
    if (preview == null) return stringResource(R.string.preview_no_messages)
    val core = when {
        preview.lastIsDeleted -> stringResource(R.string.preview_deleted)
        else -> when (preview.lastKind) {
            DomainMessageKind.IMAGE -> stringResource(R.string.preview_photo)
            DomainMessageKind.VIDEO -> stringResource(R.string.preview_video)
            DomainMessageKind.AUDIO -> stringResource(R.string.preview_voice)
            DomainMessageKind.FILE -> stringResource(R.string.preview_file)
            DomainMessageKind.TEXT -> RichBody.kindOf(preview.lastBody)?.let { stringResource(richKindLabel(it)) }
                ?: RichText.plainText(preview.lastBody.orEmpty()).replace('\n', ' ')
        }
    }
    return if (preview.lastIsOwn && !preview.lastIsDeleted) stringResource(R.string.preview_you) + " " + core else core
}

@Composable
private fun timeLabel(epochMillis: Long?): String {
    if (epochMillis == null) return ""
    val seen = LastSeenFormatter.format(epochMillis)
    return when (seen.day) {
        LastSeenFormatter.Day.TODAY -> seen.time
        LastSeenFormatter.Day.YESTERDAY -> stringResource(R.string.date_yesterday)
        LastSeenFormatter.Day.OTHER -> ChatDateFormatter.header(epochMillis).text
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    preview: ConversationPreview?,
    onClick: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
    onSetMuted: (Boolean) -> Unit,
    onBlockUser: () -> Unit = {},
    onReportUser: (category: String, details: String?) -> Unit = { _, _ -> },
    onSetArchived: (Boolean) -> Unit,
    onExport: () -> Unit,
    manuallyUnread: Boolean = false,
    isTyping: Boolean = false,
    pinLimitReached: Boolean = false,
    onToggleRead: () -> Unit = {},
    onAddToFolder: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // Lives at row level (not inside the menu) so the dialog survives the menu closing.
    var showReportUser by remember { mutableStateOf(false) }
    var showChatSound by remember { mutableStateOf(false) }
    if (showChatSound) {
        com.omidgame.mench.feature.product.ChatSoundDialog(
            conversationId = conversation.id,
            onDismiss = { showChatSound = false },
        )
    }
    if (showReportUser) {
        com.omidgame.mench.feature.product.ReportDialog(
            onDismiss = { showReportUser = false },
            onSubmit = { cat, details -> showReportUser = false; onReportUser(cat, details) },
        )
    }
    val context = LocalContext.current
    val draft = DraftStore.get(context, conversation.id)
    val unread = preview?.unreadCount ?: 0
    val hasUnread = unread > 0 || manuallyUnread
    val shape = RoundedCornerShape(20.dp)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardColor = when {
        conversation.isPinned -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.35f else 0.55f)
        dark -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val title = if (conversation.isSaved) stringResource(R.string.saved_messages_title)
        else conversation.title ?: conversation.otherUserDisplayName ?: conversation.otherUserPhoneE164 ?: "—"

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(1.dp, shape, clip = false)
                .clip(shape)
                .background(cardColor)
                .border(0.5.dp, MenchBrand.hairline(), shape)
                // Long-press opens the pin/mute menu (Telegram-style).
                .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                userId = conversation.otherUserId.takeIf { !conversation.isGroup },
                version = conversation.otherUserAvatarVersion,
                name = title,
                size = 54.dp,
                sharedKey = conversation.id,
                showStoryRing = true,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isTyping) {
                    Text(
                        text = stringResource(R.string.conversation_typing),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                } else if (draft.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.chat_draft_prefix) + " " + draft,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = if (ChatLock.isLocked(context, conversation.id)) "🔒" else previewLine(preview),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                        color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = timeLabel(preview?.lastAtEpochMillis ?: conversation.lastMessageAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread && !conversation.isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (conversation.isMuted) {
                        Icon(
                            Icons.Filled.NotificationsOff,
                            contentDescription = stringResource(R.string.conversation_muted),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (conversation.isPinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.conversation_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (manuallyUnread && unread == 0) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (conversation.isMuted) MaterialTheme.colorScheme.outline.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                ),
                        )
                    }
                    if (unread > 0) {
                        val badgeModifier = if (conversation.isMuted) {
                            Modifier.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), CircleShape)
                        } else {
                            Modifier.background(MenchBrand.heroBrush(), CircleShape)
                        }
                        Box(
                            modifier = badgeModifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (unread > 99) "99+" else unread.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
        MenchMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            MenchMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (conversation.isPinned) R.string.conversation_unpin else R.string.conversation_pin,
                        ),
                    )
                },
                // Pinned-chat limit (Phase 2A): pinning is blocked once the limit is reached; unpinning always works.
                enabled = conversation.isPinned || !pinLimitReached,
                onClick = {
                    menuExpanded = false
                    onSetPinned(!conversation.isPinned)
                },
            )
            MenchMenuItem(
                text = { Text(stringResource(if (hasUnread) R.string.chat_mark_read else R.string.chat_mark_unread)) },
                onClick = {
                    menuExpanded = false
                    onToggleRead()
                },
            )
            MenchMenuItem(
                text = { Text(stringResource(R.string.folder_add_to)) },
                onClick = {
                    menuExpanded = false
                    onAddToFolder()
                },
            )
            MenchMenuItem(
                text = { Text(stringResource(R.string.export_chat)) },
                onClick = {
                    menuExpanded = false
                    onExport()
                },
            )
            MenchMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (conversation.isArchived) R.string.chat_unarchive else R.string.chat_archive_action,
                        ),
                    )
                },
                onClick = {
                    menuExpanded = false
                    onSetArchived(!conversation.isArchived)
                },
            )
            MenchMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (conversation.isMuted) R.string.conversation_unmute else R.string.conversation_mute,
                        ),
                    )
                },
                onClick = {
                    menuExpanded = false
                    onSetMuted(!conversation.isMuted)
                },
            )
            // Phase 1C: quiet timed mutes. Stored on-device (SharedPreferences are shared per process).
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            val policy = remember { com.omidgame.mench.core.notifications.NotificationPolicy(appContext) }
            MenchMenuItem(
                text = { Text(stringResource(R.string.mute_1h)) },
                onClick = { menuExpanded = false; policy.muteFor(conversation.id, 60 * 60_000L) },
            )
            MenchMenuItem(
                text = { Text(stringResource(R.string.mute_8h)) },
                onClick = { menuExpanded = false; policy.muteFor(conversation.id, 8 * 60 * 60_000L) },
            )
            MenchMenuItem(
                text = { Text(stringResource(R.string.mute_tomorrow)) },
                onClick = { menuExpanded = false; policy.muteUntilTomorrow(conversation.id) },
            )
            if (conversation.kind == "direct" && conversation.otherUserId != null) {
                MenchMenuItem(
                    text = { Text(stringResource(R.string.block_user_action)) },
                    destructive = true,
                    onClick = { menuExpanded = false; onBlockUser() },
                )
                MenchMenuItem(
                    text = { Text(stringResource(R.string.report_user_action)) },
                    destructive = true,
                    onClick = { menuExpanded = false; showReportUser = true },
                )
            }
            // Phase 1D.5: per-chat media auto-download rule (default -> always -> never -> default).
            val autoDl = remember { com.omidgame.mench.core.media.AutoDownloadSettings(appContext) }
            var override by remember(conversation.id) { mutableStateOf(autoDl.chatOverride(conversation.id)) }
            MenchMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (override) {
                                com.omidgame.mench.core.media.ChatDownloadOverride.DEFAULT -> R.string.chat_autodl_default
                                com.omidgame.mench.core.media.ChatDownloadOverride.ALWAYS -> R.string.chat_autodl_always
                                com.omidgame.mench.core.media.ChatDownloadOverride.NEVER -> R.string.chat_autodl_never
                            },
                        ),
                    )
                },
                onClick = {
                    override = when (override) {
                        com.omidgame.mench.core.media.ChatDownloadOverride.DEFAULT -> com.omidgame.mench.core.media.ChatDownloadOverride.ALWAYS
                        com.omidgame.mench.core.media.ChatDownloadOverride.ALWAYS -> com.omidgame.mench.core.media.ChatDownloadOverride.NEVER
                        com.omidgame.mench.core.media.ChatDownloadOverride.NEVER -> com.omidgame.mench.core.media.ChatDownloadOverride.DEFAULT
                    }
                    autoDl.setChatOverride(conversation.id, override)
                },
            )
            MenchMenuItem(
                text = { Text(stringResource(R.string.chat_sound_menu)) },
                onClick = { menuExpanded = false; showChatSound = true },
            )
            if (policy.isTimedMuted(conversation.id)) {
                MenchMenuItem(
                    text = { Text(stringResource(R.string.mute_clear)) },
                    onClick = { menuExpanded = false; policy.clearTimedMute(conversation.id) },
                )
            }
        }
    }
}
