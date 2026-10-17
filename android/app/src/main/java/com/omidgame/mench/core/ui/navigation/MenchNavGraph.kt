package com.omidgame.mench.core.ui.navigation

import com.omidgame.mench.feature.auth.presentation.TwoStepEntryScreen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.omidgame.mench.core.ui.adaptive.LIST_PANE_WIDTH_DP
import com.omidgame.mench.core.ui.adaptive.isTwoPane
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.omidgame.mench.core.ui.components.AnimatedSplash
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omidgame.mench.feature.appearance.presentation.AppearanceSettingsScreen
import com.omidgame.mench.feature.appearance.presentation.AppearanceSettingsViewModel
import com.omidgame.mench.feature.auth.presentation.AuthUiState
import com.omidgame.mench.feature.auth.presentation.AuthViewModel
import com.omidgame.mench.feature.auth.presentation.OtpEntryScreen
import com.omidgame.mench.feature.auth.presentation.PhoneEntryScreen
import com.omidgame.mench.feature.calls.domain.CallType
import com.omidgame.mench.feature.calls.presentation.CallOverlay
import com.omidgame.mench.feature.calls.presentation.CallViewModel
import com.omidgame.mench.feature.chat.presentation.ChatScreen
import com.omidgame.mench.feature.chat.presentation.ChatViewModel
import com.omidgame.mench.feature.chat.presentation.ConversationListScreen
import com.omidgame.mench.feature.chat.presentation.ConversationListViewModel
import com.omidgame.mench.feature.chat.presentation.GroupInfoScreen
import com.omidgame.mench.feature.chat.presentation.GroupInfoUiState
import com.omidgame.mench.feature.chat.presentation.GroupInfoViewModel
import com.omidgame.mench.feature.chat.presentation.MediaGalleryScreen
import com.omidgame.mench.feature.chat.presentation.MediaGalleryViewModel
import com.omidgame.mench.feature.chat.presentation.NewConversationScreen
import com.omidgame.mench.feature.contacts.presentation.ContactsScreen
import com.omidgame.mench.feature.contacts.presentation.ContactsViewModel
import com.omidgame.mench.feature.chat.presentation.NewConversationUiState
import com.omidgame.mench.feature.chat.presentation.NewConversationViewModel
import com.omidgame.mench.feature.chat.presentation.NewGroupScreen
import com.omidgame.mench.feature.chat.presentation.NewGroupUiState
import com.omidgame.mench.feature.chat.presentation.NewGroupViewModel
import com.omidgame.mench.feature.diagnostics.presentation.DiagnosticsScreen
import com.omidgame.mench.feature.diagnostics.presentation.DiagnosticsViewModel
import com.omidgame.mench.feature.search.presentation.SearchScreen
import com.omidgame.mench.feature.search.presentation.SearchViewModel
import com.omidgame.mench.feature.splash.SplashDestination
import com.omidgame.mench.feature.splash.SplashViewModel
import com.omidgame.mench.feature.devices.presentation.DeviceManagementScreen
import com.omidgame.mench.feature.devices.presentation.DeviceManagementViewModel
import com.omidgame.mench.feature.security.domain.AppLockTimeoutOption
import com.omidgame.mench.feature.security.presentation.PinSetupMode
import com.omidgame.mench.feature.security.presentation.PinSetupScreen
import com.omidgame.mench.feature.security.presentation.PinSetupViewModel
import com.omidgame.mench.feature.security.presentation.PrivacySecurityScreen
import com.omidgame.mench.feature.security.presentation.PrivacySecurityViewModel
import com.omidgame.mench.feature.settings.presentation.AccountSettingsScreen
import com.omidgame.mench.feature.settings.presentation.AccountSettingsViewModel
import com.omidgame.mench.feature.settings.presentation.DataStorageScreen
import com.omidgame.mench.feature.settings.presentation.DataStorageViewModel
import com.omidgame.mench.feature.settings.presentation.NotificationPrivacyScreen
import com.omidgame.mench.feature.settings.presentation.NotificationPrivacyViewModel
import com.omidgame.mench.feature.settings.domain.SettingsCategory
import com.omidgame.mench.feature.settings.domain.SettingsResetPlan
import com.omidgame.mench.feature.settings.domain.SettingsTarget
import com.omidgame.mench.feature.settings.presentation.AboutScreen
import com.omidgame.mench.feature.settings.presentation.AppearanceExtrasScreen
import com.omidgame.mench.feature.settings.presentation.CallsSettingsScreen
import com.omidgame.mench.feature.settings.presentation.DataCleanupScreen
import com.omidgame.mench.feature.settings.presentation.LanguageRegionScreen
import com.omidgame.mench.feature.settings.presentation.ChatsSettingsScreen
import com.omidgame.mench.feature.settings.presentation.UsSettingsScreen
import com.omidgame.mench.feature.settings.presentation.DataExportScreen
import com.omidgame.mench.feature.settings.presentation.NotificationsHubScreen
import com.omidgame.mench.feature.settings.presentation.SecurityHubScreen
import com.omidgame.mench.feature.settings.presentation.SettingsCategoryScreen
import com.omidgame.mench.feature.settings.presentation.SettingsScreen
import com.omidgame.mench.feature.settings.presentation.SettingsViewModel

private object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val CONVERSATIONS = "conversations"
    const val NEW_CONVERSATION = "conversations/new"
    const val CONTACTS = "contacts"
    const val NEW_GROUP = "conversations/new-group"
    const val CHAT = "conversations/{conversationId}"
    const val GROUP_INFO = "conversations/{conversationId}/info"
    const val MEDIA_GALLERY = "conversations/{conversationId}/media"
    const val SETTINGS = "settings"
    /** Phase 1A: one screen per settings category; optional highlight = registry id to scroll to and flash. */
    const val SETTINGS_CATEGORY = "settings/category/{key}?highlight={highlight}"
    const val APPEARANCE_SETTINGS = "settings/appearance"
    const val ACCOUNT_SETTINGS = "settings/account"
    const val PRIVACY_SECURITY = "settings/privacy-security"
    const val SECURITY_HUB = "settings/security-hub"
    const val DATA_EXPORT = "settings/data-export"
    const val CHATS_SETTINGS = "settings/chats"
    const val US_SETTINGS = "settings/us"
    const val CALLS_SETTINGS = "settings/calls"
    const val DATA_CLEANUP = "settings/data-cleanup"
    const val APPEARANCE_EXTRAS = "settings/appearance-extras"
    const val LANGUAGE_REGION = "settings/language"
    const val ABOUT = "settings/about"
    const val DEVICE_MANAGEMENT = "settings/devices"
    const val DATA_STORAGE = "settings/data-storage"
    const val NOTIFICATION_PRIVACY = "settings/notification-privacy"
    const val NOTIFICATIONS_HUB = "settings/notifications-hub"
    const val PRIVACY = "settings/privacy"
    const val TWO_STEP = "settings/two-step"
    const val PIN_SETUP = "settings/privacy-security/pin/{mode}"
    const val SEARCH = "search"
    const val DIAGNOSTICS = "diagnostics"

    fun chat(conversationId: String) = "conversations/$conversationId"
    fun groupInfo(conversationId: String) = "conversations/$conversationId/info"
    fun mediaGallery(conversationId: String) = "conversations/$conversationId/media"
    fun pinSetup(mode: String) = "settings/privacy-security/pin/$mode"
    fun settingsCategory(key: String, highlight: String? = null) =
        "settings/category/$key" + if (highlight != null) "?highlight=$highlight" else ""
}

@androidx.compose.animation.ExperimentalSharedTransitionApi
@Composable
fun MenchNavGraph(
    navController: NavHostController = rememberNavController(),
    pendingConversationId: String? = null,
    onPendingConversationConsumed: () -> Unit = {},
    /** Phase 1C: a deep link (mench:// or https app link) already resolved to an in-app route. */
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
) {
    val productPrefs = androidx.compose.runtime.remember(
        androidx.compose.ui.platform.LocalContext.current,
    ) { com.omidgame.mench.feature.product.ProductPrefs(navController.context.applicationContext) }
    // Scoped to whatever owns this composable's position in the tree —
    // MainActivity, per MainActivity.kt calling MenchNavGraph() directly
    // under setContent — so this is exactly one instance for the app's
    // whole lifetime, backed by the CallSessionManager singleton
    // regardless of which destination is on screen when a call starts or
    // arrives. See CallSessionManager's doc comment.
    val callViewModel: CallViewModel = hiltViewModel()
    val callState by callViewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.animation.SharedTransitionLayout {
    androidx.compose.runtime.CompositionLocalProvider(
        com.omidgame.mench.core.ui.components.LocalSharedTransitionScope provides this,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        // Forward: new screen glides in from the "start" edge (RTL-aware) with a fade;
        // the old one drifts back slightly. Back reverses it. Splash/Auth hand-offs just cross-fade.
        enterTransition = {
            if (initialState.destination.route == Routes.SPLASH || initialState.destination.route == Routes.AUTH) {
                fadeIn(tween(450))
            } else {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(340, easing = FastOutSlowInEasing),
                    initialOffset = { it / 4 },
                ) + fadeIn(tween(340))
            }
        },
        exitTransition = {
            if (initialState.destination.route == Routes.SPLASH || initialState.destination.route == Routes.AUTH) {
                fadeOut(tween(300))
            } else {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(340, easing = FastOutSlowInEasing),
                    targetOffset = { it / 6 },
                ) + fadeOut(tween(240))
            }
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(320, easing = FastOutSlowInEasing),
                initialOffset = { it / 6 },
            ) + fadeIn(tween(320))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(320, easing = FastOutSlowInEasing),
                targetOffset = { it / 4 },
            ) + fadeOut(tween(240))
        },
    ) {
        composable(Routes.SPLASH) {
            SplashRoute(
                onNavigateToAuth = {
                    navController.navigate(Routes.AUTH) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
                onNavigateToHome = {
                    navController.navigate(Routes.CONVERSATIONS) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
            )
        }
        composable(Routes.AUTH) {
            AuthRoute(
                onSignedIn = {
                    // First sign-in on this install: intro + permission primers + optional profile.
                    val next = if (productPrefs.onboardingDone) Routes.CONVERSATIONS else com.omidgame.mench.feature.product.ProductRoutes.ONBOARDING
                    navController.navigate(next) { popUpTo(Routes.AUTH) { inclusive = true } }
                },
            )
        }
        composable(Routes.CONVERSATIONS) {
            val unseenStories by hiltViewModel<ConversationListViewModel>().unseenStoryConversations.collectAsStateWithLifecycle()
            androidx.compose.runtime.CompositionLocalProvider(
                com.omidgame.mench.core.ui.components.LocalAnimatedVisibilityScope provides this@composable,
                com.omidgame.mench.core.ui.components.LocalUnseenStories provides unseenStories,
            ) {
            val viewModel: ConversationListViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val joinState by viewModel.joinState.collectAsStateWithLifecycle()

            // Tablet / foldable / landscape: list + chat side by side (Phase 4A).
            val wide = isTwoPane(LocalConfiguration.current.screenWidthDp)
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
            val detailNav = rememberNavController()

            // Phase 6: a notification tap that arrived while the app was
            // cold (or already running but on some other screen) surfaces
            // here — the conversation list is the first screen reachable
            // after both Splash's sign-in check AND Auth's sign-in flow,
            // so it is the one place a pending deep link is guaranteed to
            // be able to actually navigate onward from. Consumed
            // immediately so returning to this screen later (e.g. via back
            // button) doesn't re-trigger the same navigation.
            LaunchedEffect(pendingConversationId) {
                if (pendingConversationId != null) {
                    if (wide) selectedId = pendingConversationId else navController.navigate(Routes.chat(pendingConversationId))
                    onPendingConversationConsumed()
                }
            }

            LaunchedEffect(pendingRoute) {
                if (pendingRoute != null) {
                    navController.navigate(pendingRoute)
                    onPendingRouteConsumed()
                }
            }

            val exportScope = androidx.compose.runtime.rememberCoroutineScope()
            val exportContext = androidx.compose.ui.platform.LocalContext.current
            val listPane: @Composable () -> Unit = {
            ConversationListScreen(
                state = state,
                onConversationClick = { id -> if (wide) selectedId = id else navController.navigate(Routes.chat(id)) },
                onNewConversationClick = { navController.navigate(Routes.NEW_CONVERSATION) },
                onNewGroupClick = { navController.navigate(Routes.NEW_GROUP) },
                joinState = joinState,
                onLookupInvite = viewModel::onLookupInvite,
                onConfirmJoin = viewModel::onConfirmJoin,
                onJoinDismissed = viewModel::onJoinDismissed,
                onContactsClick = { navController.navigate(Routes.CONTACTS) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onOpenSaved = { viewModel.onOpenSaved { id -> navController.navigate(Routes.chat(id)) } },
                onSetPinned = viewModel::onSetPinned,
                onSetMuted = viewModel::onSetMuted,
                onBlockUser = viewModel::onBlockUser,
                onReportUser = viewModel::onReportUser,
                onSetArchived = viewModel::onSetArchived,
                onToggleArchivedView = viewModel::onToggleArchivedView,
                onSelectFilter = viewModel::onSelectFilter,
                onCreateFolder = viewModel::onCreateFolder,
                onRenameFolder = viewModel::onRenameFolder,
                onDeleteFolder = viewModel::onDeleteFolder,
                onSetFolderMembership = viewModel::onSetFolderMembership,
                onMarkUnread = viewModel::onMarkUnread,
                onMarkRead = viewModel::onMarkRead,
                onConversationOpened = viewModel::onConversationOpened,
                onCallBack = { conversationId, callType -> callViewModel.startCall(conversationId, callType) },
                onRefresh = viewModel::refresh,
                onExport = { conversation ->
                    exportScope.launch {
                        val messages = viewModel.messagesForExport(conversation.id)
                        val other = conversation.title ?: conversation.otherUserDisplayName ?: conversation.otherUserPhoneE164 ?: "—"
                        val text = com.omidgame.mench.feature.chat.presentation.ChatExporter.buildText(
                            exportContext, messages, exportContext.getString(com.omidgame.mench.R.string.export_me), other,
                        )
                        com.omidgame.mench.feature.chat.presentation.ChatExporter.share(exportContext, other, text)
                    }
                },
            )
            }
            }

            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    MenchRail(
                        onContacts = { navController.navigate(Routes.CONTACTS) },
                        onSearch = { navController.navigate(Routes.SEARCH) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                    Box(modifier = Modifier.width(LIST_PANE_WIDTH_DP.dp).fillMaxHeight()) { listPane() }
                    VerticalDivider()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // Hardware keyboard: Esc closes the open chat.
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.Escape && event.type == KeyEventType.KeyUp && selectedId != null) {
                                    selectedId = null
                                    true
                                } else {
                                    false
                                }
                            },
                    ) {
                        TwoPaneDetail(
                            selectedId = selectedId,
                            detailNav = detailNav,
                            callViewModel = callViewModel,
                            onClose = { selectedId = null },
                            onGroupInfo = { id -> navController.navigate(Routes.groupInfo(id)) },
                            onMediaGallery = { id -> navController.navigate(Routes.mediaGallery(id)) },
                        )
                    }
                }
            } else {
                listPane()
            }
        }
        com.omidgame.mench.feature.product.productRoutes(
            navController = navController,
            onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
            onLoggedOut = { navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } } },
            onOnboardingFinished = {
                navController.navigate(Routes.CONVERSATIONS) {
                    popUpTo(com.omidgame.mench.feature.product.ProductRoutes.ONBOARDING) { inclusive = true }
                }
            },
        )
        composable(Routes.CONTACTS) {
            val viewModel: ContactsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.navigateToConversation.collect { conversationId ->
                    navController.navigate(Routes.chat(conversationId)) {
                        popUpTo(Routes.CONVERSATIONS)
                    }
                }
            }

            ContactsScreen(
                state = state,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onContactClick = viewModel::onContactClick,
                onPermissionGranted = viewModel::loadContacts,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.NEW_CONVERSATION) {
            val viewModel: NewConversationViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state) {
                val current = state
                if (current is NewConversationUiState.Started) {
                    navController.navigate(Routes.chat(current.conversationId)) {
                        popUpTo(Routes.CONVERSATIONS)
                    }
                }
            }

            NewConversationScreen(state = state, onSubmit = viewModel::onSubmitPhone)
        }
        composable(Routes.NEW_GROUP) {
            val viewModel: NewGroupViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state) {
                val current = state
                if (current is NewGroupUiState.Created) {
                    navController.navigate(Routes.chat(current.conversationId)) {
                        popUpTo(Routes.CONVERSATIONS)
                    }
                }
            }

            NewGroupScreen(state = state, onSubmit = viewModel::onSubmit)
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            androidx.compose.runtime.CompositionLocalProvider(com.omidgame.mench.core.ui.components.LocalAnimatedVisibilityScope provides this@composable) {
            val conversationId = checkNotNull(backStackEntry.arguments?.getString("conversationId"))
            ChatRoute(
                conversationId = conversationId,
                callViewModel = callViewModel,
                onBack = { navController.popBackStack() },
                onGroupInfo = { navController.navigate(Routes.groupInfo(conversationId)) },
                onMediaGallery = { navController.navigate(Routes.mediaGallery(conversationId)) },
            )
            }
        }
        composable(
            route = Routes.GROUP_INFO,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) {
            val viewModel: GroupInfoViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state) {
                if (state is GroupInfoUiState.Left) {
                    navController.navigate(Routes.CONVERSATIONS) {
                        popUpTo(Routes.CONVERSATIONS) { inclusive = true }
                    }
                }
            }

            GroupInfoScreen(
                state = state,
                onRename = viewModel::onRename,
                onAddMember = viewModel::onAddMember,
                onRemoveMember = viewModel::onRemoveMember,
                onSettingsChanged = viewModel::onSettingsChanged,
                onToggleAdmin = viewModel::onToggleAdmin,
                onTransferOwner = viewModel::onTransferOwner,
                onCreateInvite = viewModel::onCreateInvite,
                onRevokeInvite = viewModel::onRevokeInvite,
            )
        }
        composable(
            route = Routes.MEDIA_GALLERY,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) {
            val viewModel: MediaGalleryViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            MediaGalleryScreen(
                state = state,
                onTabSelected = viewModel::onTabSelected,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshSecurityFacts() }
            // Settings Phase 2: tablet / foldable / landscape shows the list and the chosen category side by side.
            val wide = isTwoPane(LocalConfiguration.current.screenWidthDp)
            var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.ACCOUNT.routeKey) }
            val settingsList: @Composable () -> Unit = {
                SettingsScreen(
                    state = state,
                    onOpenCategory = { if (wide) selectedCategory = it.routeKey else navController.navigate(Routes.settingsCategory(it.routeKey)) },
                    onResetAll = {
                        viewModel.resetSettings(null) {
                            android.widget.Toast.makeText(navController.context, navController.context.getString(com.omidgame.mench.R.string.sx_reset_done), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenSearchHit = { navController.navigate(Routes.settingsCategory(it.category.routeKey, it.id)) },
                    onAccountClick = { navController.navigate(Routes.ACCOUNT_SETTINGS) },
                    onAppearanceClick = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
                    onPrivacySecurityClick = { navController.navigate(Routes.PRIVACY_SECURITY) },
                    onDataStorageClick = { navController.navigate(Routes.DATA_STORAGE) },
                    onNotificationsClick = { navController.navigate(Routes.NOTIFICATION_PRIVACY) },
                    onDiagnosticsClick = { navController.navigate(Routes.DIAGNOSTICS) },
                    onProductClick = { key ->
                        com.omidgame.mench.feature.product.ProductRoutes.forSettingsKey(key)?.let { navController.navigate(it) }
                    },
                    onLogout = viewModel::onLogout,
                    onLoggedOut = {
                        navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                    },
                    onBackClick = { navController.popBackStack() },
                )
            }
            if (wide) {
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.width(LIST_PANE_WIDTH_DP.dp).fillMaxHeight()) { settingsList() }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        SettingsCategoryScreen(
                            category = SettingsCategory.fromRouteKey(selectedCategory) ?: SettingsCategory.ACCOUNT,
                            highlightId = null,
                            onNavigate = { target ->
                                when (target) {
                                    is SettingsTarget.Route -> navController.navigate(target.route)
                                    is SettingsTarget.Product ->
                                        com.omidgame.mench.feature.product.ProductRoutes.forSettingsKey(target.key)?.let { navController.navigate(it) }
                                    is SettingsTarget.Action -> Unit
                                }
                            },
                            onBack = {},
                            embedded = true,
                        )
                    }
                }
            } else {
                settingsList()
            }
        }
        composable(
            route = Routes.SETTINGS_CATEGORY,
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("highlight") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val category = SettingsCategory.fromRouteKey(entry.arguments?.getString("key"))
            if (category == null) {
                // Unknown deep link: fall back to the settings home instead of showing a blank screen.
                androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                val settingsVm: SettingsViewModel = hiltViewModel()
                SettingsCategoryScreen(
                    category = category,
                    onReset = if (SettingsResetPlan.canReset(category)) {
                        {
                            settingsVm.resetSettings(category) {
                                android.widget.Toast.makeText(navController.context, navController.context.getString(com.omidgame.mench.R.string.sx_reset_done), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        null
                    },
                    highlightId = entry.arguments?.getString("highlight"),
                    onNavigate = { target ->
                        when (target) {
                            is SettingsTarget.Route -> navController.navigate(target.route)
                            is SettingsTarget.Product ->
                                com.omidgame.mench.feature.product.ProductRoutes.forSettingsKey(target.key)
                                    ?.let { navController.navigate(it) }
                            is SettingsTarget.Action -> Unit // handled inside SettingsCategoryScreen
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Routes.APPEARANCE_SETTINGS) {
            val viewModel: AppearanceSettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AppearanceSettingsScreen(
                state = state,
                onThemeModeSelected = viewModel::onThemeModeSelected,
                onAccentColorSelected = viewModel::onAccentColorSelected,
                onFontScaleSelected = viewModel::onFontScaleSelected,
                onBubbleStyleSelected = viewModel::onBubbleStyleSelected,
                onDynamicColorSelected = viewModel::onDynamicColorSelected,
                onAmoledSelected = viewModel::onAmoledSelected,
                onPresetSelected = viewModel::onPresetSelected,
                onDigitStyleSelected = viewModel::onDigitStyleSelected,
                onCalendarStyleSelected = viewModel::onCalendarStyleSelected,
                onScreenSecurityChanged = viewModel::onScreenSecurityChanged,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.ACCOUNT_SETTINGS) {
            val viewModel: AccountSettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AccountSettingsScreen(
                state = state,
                onDisplayNameChange = viewModel::onDisplayNameChange,
                onUsernameChange = viewModel::onUsernameChange,
                onBioChange = viewModel::onBioChange,
                onAvatarPicked = viewModel::onAvatarPicked,
                onSave = viewModel::onSave,
                onSaved = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onOpenLegal = { com.omidgame.mench.feature.product.ProductRoutes.forSettingsKey("legal")?.let { navController.navigate(it) } },
                onOpenChangelog = { com.omidgame.mench.feature.product.ProductRoutes.forSettingsKey("changelog")?.let { navController.navigate(it) } },
            )
        }
        composable(Routes.LANGUAGE_REGION) {
            LanguageRegionScreen(
                onBack = { navController.popBackStack() },
                onOpenAppearance = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
            )
        }
        composable(Routes.APPEARANCE_EXTRAS) { AppearanceExtrasScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DATA_CLEANUP) {
            DataCleanupScreen(
                onBack = { navController.popBackStack() },
                onOpenDataUsage = {
                    com.omidgame.mench.feature.product.ProductRoutes.forSettingsKey("data_usage")?.let { navController.navigate(it) }
                },
                onOpenSecurityHub = { navController.navigate(Routes.SECURITY_HUB) },
            )
        }
        composable(Routes.CALLS_SETTINGS) { CallsSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.US_SETTINGS) { UsSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CHATS_SETTINGS) {
            ChatsSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { navController.navigate(Routes.chat(it)) },
            )
        }
        composable(Routes.DATA_EXPORT) { DataExportScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATIONS_HUB) {
            NotificationsHubScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.NOTIFICATION_PRIVACY) },
            )
        }
        composable(Routes.SECURITY_HUB) {
            SecurityHubScreen(
                onBack = { navController.popBackStack() },
                onOpenAppLock = { navController.navigate(Routes.PRIVACY_SECURITY) },
                onOpenTwoStep = { navController.navigate(Routes.TWO_STEP) },
                onOpenDevices = { navController.navigate(Routes.DEVICE_MANAGEMENT) },
                onOpenBlocked = {
                    com.omidgame.mench.feature.product.ProductRoutes.forSettingsKey("blocked")?.let { navController.navigate(it) }
                },
            )
        }
        composable(Routes.PRIVACY_SECURITY) {
            val viewModel: PrivacySecurityViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PrivacySecurityScreen(
                state = state,
                onResumed = viewModel::refresh,
                onAppLockEnableRequested = { navController.navigate(Routes.pinSetup(PinSetupMode.CREATE.name)) },
                onAppLockDisableRequested = { navController.navigate(Routes.pinSetup(PinSetupMode.DISABLE.name)) },
                onChangePinRequested = { navController.navigate(Routes.pinSetup(PinSetupMode.CHANGE.name)) },
                onBiometricToggle = viewModel::onBiometricToggle,
                onTimeoutSelected = viewModel::onTimeoutSelected,
                onLogoutAllDevices = viewModel::onLogoutAllDevices,
                onLoggedOutAll = {
                    navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                },
                onManageDevicesClick = { navController.navigate(Routes.DEVICE_MANAGEMENT) },
                onPrivacyClick = { navController.navigate(Routes.PRIVACY) },
                onTwoStepClick = { navController.navigate(Routes.TWO_STEP) },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.DEVICE_MANAGEMENT) {
            val viewModel: DeviceManagementViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            DeviceManagementScreen(
                state = state,
                onRevokeDevice = viewModel::revokeDevice,
                onBackClick = { navController.popBackStack() },
                onRevokeOthers = viewModel::revokeOtherDevices,
            )
        }
        composable(Routes.DATA_STORAGE) {
            val viewModel: DataStorageViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            DataStorageScreen(
                state = state,
                onClearCache = viewModel::onClearCache,
                onClearImageCache = viewModel::onClearImageCache,
                onToggleRule = viewModel::onToggleRule,
                onMaxMobileSizeChanged = viewModel::onMaxMobileSizeChanged,
                onClearType = viewModel::onClearType,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.PRIVACY) {
            val viewModel: com.omidgame.mench.feature.privacy.presentation.PrivacyViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            com.omidgame.mench.feature.privacy.presentation.PrivacyScreen(
                state = state,
                onChange = viewModel::update,
                onRetry = viewModel::load,
                onTwoStepClick = { navController.navigate(Routes.TWO_STEP) },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.TWO_STEP) {
            val viewModel: com.omidgame.mench.feature.privacy.presentation.TwoStepSettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            com.omidgame.mench.feature.privacy.presentation.TwoStepSettingsScreen(
                state = state,
                onSave = viewModel::save,
                onDisable = viewModel::disable,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.NOTIFICATION_PRIVACY) {
            val viewModel: NotificationPrivacyViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            NotificationPrivacyScreen(
                state = state,
                onSelect = viewModel::onSelect,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PIN_SETUP,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) {
            val viewModel: PinSetupViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.completed) {
                if (state.completed) navController.popBackStack()
            }

            PinSetupScreen(
                state = state,
                onSubmit = viewModel::onSubmit,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.SEARCH) {
            val viewModel: SearchViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val query by viewModel.query.collectAsStateWithLifecycle()
            val scope by viewModel.scope.collectAsStateWithLifecycle()
            SearchScreen(
                query = query,
                scope = scope,
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onScopeChange = viewModel::onScopeChange,
                onConversationClick = { id ->
                    navController.navigate(Routes.chat(id)) { popUpTo(Routes.CONVERSATIONS) }
                },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.DIAGNOSTICS) {
            val viewModel: DiagnosticsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            DiagnosticsScreen(
                state = state,
                onRerun = viewModel::runDiagnostics,
                onBackClick = { navController.popBackStack() },
            )
        }
    }

    CallOverlay(state = callState, viewModel = callViewModel)
    }
    }
    }
}

@Composable
private fun SplashRoute(onNavigateToAuth: () -> Unit, onNavigateToHome: () -> Unit) {
    val viewModel: SplashViewModel = hiltViewModel()
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    // Keep the animated splash on screen long enough for one or two heartbeats,
    // even when the session check finishes instantly.
    var minTimeElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1300)
        minTimeElapsed = true
    }

    LaunchedEffect(destination, minTimeElapsed) {
        if (!minTimeElapsed) return@LaunchedEffect
        when (destination) {
            SplashDestination.Auth -> onNavigateToAuth()
            SplashDestination.Home -> onNavigateToHome()
            SplashDestination.Loading -> Unit
        }
    }

    AnimatedSplash()
}

@Composable
private fun AuthRoute(onSignedIn: () -> Unit) {
    val viewModel: AuthViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) onSignedIn()
    }

    when (val current = state) {
        // One call site for all three phone-screen states, so the typed number is kept and errors are shown.
        is AuthUiState.PhoneEntry, is AuthUiState.RequestingOtp, is AuthUiState.Error -> PhoneEntryScreen(
            isLoading = current is AuthUiState.RequestingOtp,
            errorReason = (current as? AuthUiState.Error)?.reason,
            onSubmit = viewModel::onSubmitPhone,
        )
        is AuthUiState.OtpEntry -> OtpEntryScreen(
            state = current,
            onSubmitCode = { code -> viewModel.onSubmitCode(code, deviceName = androidDeviceName()) },
            onResend = viewModel::onResendRequested,
        )
        is AuthUiState.TwoStepEntry -> TwoStepEntryScreen(state = current, onSubmit = viewModel::onSubmitTwoStepPassword)
        is AuthUiState.SignedIn -> Unit // handled by LaunchedEffect above
    }
}

private fun androidDeviceName(): String =
    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()

/**
 * One chat screen wired to its ViewModel. Shared by the full-screen CHAT destination (phones)
 * and the detail pane of the tablet two-pane layout. [ChatViewModel] reads `conversationId` from
 * the SavedStateHandle of the NavBackStackEntry it is created under, so both hosts must be a
 * NavHost destination whose route has a {conversationId} argument.
 */
@Composable
private fun ChatRoute(
    conversationId: String,
    callViewModel: CallViewModel,
    onBack: () -> Unit,
    onGroupInfo: () -> Unit,
    onMediaGallery: () -> Unit,
) {
    // Phase 4C: a locked chat asks for biometrics / the screen lock every time it is opened.
    val context = androidx.compose.ui.platform.LocalContext.current
    var unlocked by rememberSaveable(conversationId) { mutableStateOf(!com.omidgame.mench.feature.chat.presentation.ChatLock.isLocked(context, conversationId)) }
    if (!unlocked) {
        com.omidgame.mench.feature.chat.presentation.ChatLockGate(onUnlocked = { unlocked = true }, onBack = onBack)
    } else {
        ChatRouteUnlocked(conversationId, callViewModel, onBack, onGroupInfo, onMediaGallery)
    }
}

@Composable
private fun ChatRouteUnlocked(
    conversationId: String,
    callViewModel: CallViewModel,
    onBack: () -> Unit,
    onGroupInfo: () -> Unit,
    onMediaGallery: () -> Unit,
) {
    // Phase 4C: disappearing-messages timer for this conversation (banner + dialog).
    val disappearingViewModel: com.omidgame.mench.feature.privacy.presentation.DisappearingViewModel = hiltViewModel()
    val disappearingState by disappearingViewModel.uiState.collectAsStateWithLifecycle()
    var showDisappearingDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(conversationId) { disappearingViewModel.bind(conversationId) }
    if (showDisappearingDialog) {
        com.omidgame.mench.feature.privacy.presentation.DisappearingDialog(
            state = disappearingState,
            onSelect = disappearingViewModel::set,
            onDismiss = {
                showDisappearingDialog = false
                disappearingViewModel.clearError()
            },
        )
    }
            val viewModel: ChatViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
            val replyingToMessage by viewModel.replyingToMessage.collectAsStateWithLifecycle()
            val playbackState by viewModel.voicePlayback.state.collectAsStateWithLifecycle()
            val otherPresence by viewModel.otherPresence.collectAsStateWithLifecycle()
            val letters by viewModel.letters.collectAsStateWithLifecycle()
            val memories by viewModel.memories.collectAsStateWithLifecycle()
            val counters by viewModel.counters.collectAsStateWithLifecycle()
            val canvasStrokes by viewModel.canvasStrokes.collectAsStateWithLifecycle()
            val stories by viewModel.stories.collectAsStateWithLifecycle()
            val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()
            val quizzes by viewModel.quizzes.collectAsStateWithLifecycle()
            val yearMessages by viewModel.yearMessageStats.collectAsStateWithLifecycle()
            val albumItems by viewModel.albumItems.collectAsStateWithLifecycle()
            val moods by viewModel.moods.collectAsStateWithLifecycle()
            val question by viewModel.question.collectAsStateWithLifecycle()
            val wishes by viewModel.wishes.collectAsStateWithLifecycle()
            val recorderLive by viewModel.recorderLive.collectAsStateWithLifecycle()
            val scheduled by viewModel.scheduled.collectAsStateWithLifecycle()
            val groupInfo by viewModel.groupInfo.collectAsStateWithLifecycle()
            val selfUserId by viewModel.selfUserId.collectAsStateWithLifecycle()
            ChatScreen(
                disappearingSeconds = disappearingState.seconds,
                onDisappearingClick = { showDisappearingDialog = true },
                state = state,
                selfUserId = selfUserId,
                recorderLive = recorderLive,
                voiceUi = viewModel.voiceUi,
                pollUi = viewModel.pollUi,
                onCreatePoll = viewModel::onCreatePoll,
                mediaAuthHeaders = viewModel.mediaAuthHeaders,
                onSendRoundVideo = viewModel::onSendRoundVideo,
                canPost = groupInfo?.canPost ?: true,
                onRefreshGroupInfo = viewModel::refreshGroupInfo,
                onLoadSeenBy = viewModel::loadSeenBy,
                onLoadEditHistory = viewModel::loadEditHistory,
                onTranslate = viewModel::translate,
                scheduled = scheduled,
                scheduleErrors = viewModel.scheduleErrors,
                onSendSilently = { viewModel.onSendMessage(it, silent = true) },
                onScheduleMessage = viewModel::onScheduleMessage,
                onCancelScheduled = viewModel::onCancelScheduled,
                onRefreshScheduled = viewModel::refreshScheduled,
                onForwardMany = viewModel::onForwardMany,
                onDeleteMany = viewModel::onDeleteMany,
                otherPresence = otherPresence,
                missYouEvents = viewModel.missYouEvents,
                onSendMissYou = viewModel::onSendMissYou,
                heartbeatEvents = viewModel.heartbeatEvents,
                onSendHeartbeat = viewModel::onSendHeartbeat,
                screenEffectEvents = viewModel.screenEffectEvents,
                onSendScreenEffect = viewModel::onSendScreenEffect,
                togetherEvents = viewModel.togetherEvents,
                onSendTogether = viewModel::onSendTogether,
                onSetSecureChat = viewModel::setSecureChat,
                onSetUsDates = viewModel::onSetUsDates,
                onLoadOlder = viewModel::loadOlderMessages,
                onLoadAll = viewModel::loadAllMessages,
                question = question,
                onRefreshQuestion = viewModel::refreshQuestion,
                onAnswerQuestion = viewModel::onAnswerQuestion,
                wishes = wishes,
                onRefreshWishes = viewModel::refreshWishes,
                onAddWish = viewModel::onAddWish,
                onToggleWish = viewModel::onToggleWish,
                onDeleteWish = viewModel::onDeleteWish,
                onPinMessage = viewModel::onPinMessage,
                onUnpinMessage = viewModel::onUnpinMessage,
                onLoadLinkPreview = viewModel::loadLinkPreview,
                moods = moods,
                onSetMood = viewModel::onSetMood,
                memories = memories,
                counters = counters,
                onRefreshCounters = viewModel::refreshCounters,
                canvasStrokes = canvasStrokes,
                onRefreshCanvas = viewModel::refreshCanvas,
                stories = stories,
                onRefreshStories = viewModel::refreshStories,
                vaultItems = vaultItems,
                onRefreshVault = viewModel::refreshVault,
                quizzes = quizzes,
                onRefreshQuizzes = viewModel::refreshQuizzes,
                yearMessages = yearMessages,
                albumItems = albumItems,
                onRefreshAlbum = viewModel::refreshAlbum,
                onAddAlbumPhoto = viewModel::onAddAlbumPhoto,
                onReactAlbumItem = viewModel::onReactAlbumItem,
                onDeleteAlbumItem = viewModel::onDeleteAlbumItem,
                onLoadYearMessages = viewModel::loadYearMessageStats,
                onCreateQuiz = viewModel::onCreateQuiz,
                onLoadQuiz = viewModel::onLoadQuiz,
                onSubmitQuiz = viewModel::onSubmitQuiz,
                onDeleteQuiz = viewModel::onDeleteQuiz,
                onAddVaultItem = viewModel::onAddVaultItem,
                onDeleteVaultItem = viewModel::onDeleteVaultItem,
                onCreateStory = viewModel::onCreateStory,
                onViewStory = viewModel::onViewStory,
                onDeleteStory = viewModel::onDeleteStory,
                onAppendStrokes = viewModel::onAppendStrokes,
                onClearCanvas = viewModel::onClearCanvas,
                onCreateCounter = viewModel::onCreateCounter,
                onBumpCounter = viewModel::onBumpCounter,
                onDeleteCounter = viewModel::onDeleteCounter,
                onRefreshMemories = viewModel::refreshMemories,
                onDeleteMemory = viewModel::onDeleteMemory,
                onCreateMemory = viewModel::onCreateMemory,
                affectionEvents = viewModel.affectionEvents,
                onSendAffection = viewModel::onSendAffection,
                letters = letters,
                onRefreshLetters = viewModel::refreshLetters,
                onOpenLetter = viewModel::onOpenLetter,
                onCreateLetter = viewModel::onCreateLetter,
                isRecording = isRecording,
                playbackState = playbackState,
                onSend = viewModel::onSendMessage,
                onSendAttachment = viewModel::onSendAttachment,
                onStartRecording = viewModel::onStartRecording,
                onStopRecordingAndSend = viewModel::onStopRecordingAndSend,
                onCancelRecording = viewModel::onCancelRecording,
                onTogglePlayback = viewModel::onToggleVoicePlayback,
                onMessagesRead = viewModel::onMessagesRead,
                onStartEdit = viewModel::onStartEdit,
                onCancelEdit = viewModel::onCancelEdit,
                onSubmitEdit = viewModel::onSubmitEdit,
                onDeleteMessage = viewModel::onDeleteMessage,
                onRetryFailed = viewModel::onRetryFailed,
                onDiscardFailed = viewModel::onDiscardFailed,
                onCancelUpload = viewModel::onCancelUpload,
                onReact = viewModel::onReact,
                onForward = viewModel::onForward,
                replyingToMessage = replyingToMessage,
                onReplyToMessage = viewModel::onReplyToMessage,
                onCancelReply = viewModel::onCancelReply,
                onBackClick = onBack,
                onGroupInfoClick = onGroupInfo,
                onMediaGalleryClick = onMediaGallery,
                onVoiceCallClick = { callViewModel.startCall(conversationId, CallType.VOICE) },
                onVideoCallClick = { callViewModel.startCall(conversationId, CallType.VIDEO) },
                onSendPhotos = viewModel::onSendPhotos,
            )
}

private const val DETAIL_EMPTY = "detail/empty"
private const val DETAIL_CHAT = "detail/chat/{conversationId}"

/** Right-hand pane: its own small NavHost so every chat gets a proper per-conversation ViewModel. */
@Composable
private fun TwoPaneDetail(
    selectedId: String?,
    detailNav: NavHostController,
    callViewModel: CallViewModel,
    onClose: () -> Unit,
    onGroupInfo: (String) -> Unit,
    onMediaGallery: (String) -> Unit,
) {
    LaunchedEffect(selectedId) {
        if (selectedId == null) {
            detailNav.popBackStack(DETAIL_EMPTY, false)
        } else {
            detailNav.navigate("detail/chat/$selectedId") { popUpTo(DETAIL_EMPTY) }
        }
    }
    NavHost(
        navController = detailNav,
        startDestination = DETAIL_EMPTY,
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
    ) {
        composable(DETAIL_EMPTY) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.omidgame.mench.R.string.detail_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.omidgame.mench.R.string.detail_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        composable(
            route = DETAIL_CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { entry ->
            val id = checkNotNull(entry.arguments?.getString("conversationId"))
            ChatRoute(
                conversationId = id,
                callViewModel = callViewModel,
                onBack = onClose,
                onGroupInfo = { onGroupInfo(id) },
                onMediaGallery = { onMediaGallery(id) },
            )
        }
    }
}

/** Left rail for wide screens (Phase 4A): Chats / Contacts / Search / Settings. */
@Composable
private fun MenchRail(onContacts: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    NavigationRail {
        NavigationRailItem(
            selected = true,
            onClick = {},
            icon = { Icon(Icons.Filled.Forum, contentDescription = null) },
            label = { Text(androidx.compose.ui.res.stringResource(com.omidgame.mench.R.string.rail_chats)) },
        )
        NavigationRailItem(
            selected = false,
            onClick = onContacts,
            icon = { Icon(Icons.Filled.Contacts, contentDescription = null) },
            label = { Text(androidx.compose.ui.res.stringResource(com.omidgame.mench.R.string.contacts_title)) },
        )
        NavigationRailItem(
            selected = false,
            onClick = onSearch,
            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = { Text(androidx.compose.ui.res.stringResource(com.omidgame.mench.R.string.rail_search)) },
        )
        NavigationRailItem(
            selected = false,
            onClick = onSettings,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(androidx.compose.ui.res.stringResource(com.omidgame.mench.R.string.settings_title)) },
        )
    }
}
