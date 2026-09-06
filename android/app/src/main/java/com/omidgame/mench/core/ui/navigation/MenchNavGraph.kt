package com.omidgame.mench.core.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.omidgame.mench.feature.chat.presentation.NewConversationScreen
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
import com.omidgame.mench.feature.settings.presentation.SettingsScreen
import com.omidgame.mench.feature.settings.presentation.SettingsViewModel

private object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val CONVERSATIONS = "conversations"
    const val NEW_CONVERSATION = "conversations/new"
    const val NEW_GROUP = "conversations/new-group"
    const val CHAT = "conversations/{conversationId}"
    const val GROUP_INFO = "conversations/{conversationId}/info"
    const val SETTINGS = "settings"
    const val ACCOUNT_SETTINGS = "settings/account"
    const val PRIVACY_SECURITY = "settings/privacy-security"
    const val DATA_STORAGE = "settings/data-storage"
    const val NOTIFICATION_PRIVACY = "settings/notification-privacy"
    const val PIN_SETUP = "settings/privacy-security/pin/{mode}"
    const val SEARCH = "search"
    const val DIAGNOSTICS = "diagnostics"

    fun chat(conversationId: String) = "conversations/$conversationId"
    fun groupInfo(conversationId: String) = "conversations/$conversationId/info"
    fun pinSetup(mode: String) = "settings/privacy-security/pin/$mode"
}

@Composable
fun MenchNavGraph(
    navController: NavHostController = rememberNavController(),
    pendingConversationId: String? = null,
    onPendingConversationConsumed: () -> Unit = {},
) {
    // Scoped to whatever owns this composable's position in the tree —
    // MainActivity, per MainActivity.kt calling MenchNavGraph() directly
    // under setContent — so this is exactly one instance for the app's
    // whole lifetime, backed by the CallSessionManager singleton
    // regardless of which destination is on screen when a call starts or
    // arrives. See CallSessionManager's doc comment.
    val callViewModel: CallViewModel = hiltViewModel()
    val callState by callViewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
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
                    navController.navigate(Routes.CONVERSATIONS) { popUpTo(Routes.AUTH) { inclusive = true } }
                },
            )
        }
        composable(Routes.CONVERSATIONS) {
            val viewModel: ConversationListViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                    navController.navigate(Routes.chat(pendingConversationId))
                    onPendingConversationConsumed()
                }
            }

            ConversationListScreen(
                state = state,
                onConversationClick = { id -> navController.navigate(Routes.chat(id)) },
                onNewConversationClick = { navController.navigate(Routes.NEW_CONVERSATION) },
                onNewGroupClick = { navController.navigate(Routes.NEW_GROUP) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
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
            val conversationId = checkNotNull(backStackEntry.arguments?.getString("conversationId"))
            val viewModel: ChatViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
            val playbackState by viewModel.voicePlayback.state.collectAsStateWithLifecycle()
            ChatScreen(
                state = state,
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
                onReact = viewModel::onReact,
                onForward = viewModel::onForward,
                onBackClick = { navController.popBackStack() },
                onGroupInfoClick = { navController.navigate(Routes.groupInfo(conversationId)) },
                onVoiceCallClick = { callViewModel.startCall(conversationId, CallType.VOICE) },
                onVideoCallClick = { callViewModel.startCall(conversationId, CallType.VIDEO) },
            )
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
            )
        }
        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onAccountClick = { navController.navigate(Routes.ACCOUNT_SETTINGS) },
                onPrivacySecurityClick = { navController.navigate(Routes.PRIVACY_SECURITY) },
                onDataStorageClick = { navController.navigate(Routes.DATA_STORAGE) },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATION_PRIVACY) },
                onDiagnosticsClick = { navController.navigate(Routes.DIAGNOSTICS) },
                onLogout = viewModel::onLogout,
                onLoggedOut = {
                    navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                },
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
                onSave = viewModel::onSave,
                onSaved = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() },
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
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Routes.DATA_STORAGE) {
            val viewModel: DataStorageViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            DataStorageScreen(
                state = state,
                onClearCache = viewModel::onClearCache,
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

@Composable
private fun SplashRoute(onNavigateToAuth: () -> Unit, onNavigateToHome: () -> Unit) {
    val viewModel: SplashViewModel = hiltViewModel()
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        when (destination) {
            SplashDestination.Auth -> onNavigateToAuth()
            SplashDestination.Home -> onNavigateToHome()
            SplashDestination.Loading -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthRoute(onSignedIn: () -> Unit) {
    val viewModel: AuthViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) onSignedIn()
    }

    when (val current = state) {
        is AuthUiState.PhoneEntry -> PhoneEntryScreen(isLoading = false, onSubmit = viewModel::onSubmitPhone)
        is AuthUiState.RequestingOtp -> PhoneEntryScreen(isLoading = true, onSubmit = viewModel::onSubmitPhone)
        is AuthUiState.OtpEntry -> OtpEntryScreen(
            state = current,
            onSubmitCode = { code -> viewModel.onSubmitCode(code, deviceName = androidDeviceName()) },
            onResend = viewModel::onResendRequested,
        )
        is AuthUiState.Error -> PhoneEntryScreen(isLoading = false, onSubmit = viewModel::onSubmitPhone)
        is AuthUiState.SignedIn -> Unit // handled by LaunchedEffect above
    }
}

private fun androidDeviceName(): String =
    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
