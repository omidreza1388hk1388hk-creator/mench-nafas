package com.omidgame.mench

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.omidgame.mench.feature.calls.domain.CallSessionManager
import com.omidgame.mench.feature.calls.domain.CallType
import com.omidgame.mench.feature.calls.domain.CallUiState
import com.omidgame.mench.feature.calls.presentation.CallPip
import javax.inject.Inject
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.omidgame.mench.core.security.BiometricAuthenticator
import com.omidgame.mench.core.security.BiometricResult
import com.omidgame.mench.core.ui.navigation.MenchNavGraph
import com.omidgame.mench.core.ui.theme.MenchTheme
import com.omidgame.mench.feature.appearance.domain.ThemeMode
import com.omidgame.mench.feature.appearance.presentation.ThemeSettingsViewModel
import com.omidgame.mench.feature.security.presentation.AppLockGateViewModel
import com.omidgame.mench.feature.security.presentation.LockScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * A FragmentActivity, not a plain ComponentActivity — BiometricPrompt has
 * no constructor that accepts a bare ComponentActivity (see
 * BiometricAuthenticator's doc comment). FragmentActivity itself extends
 * ComponentActivity, so setContent/Compose here work exactly as before.
 *
 * launchMode="singleTask" (see AndroidManifest.xml) means a notification
 * tap while the app is already running delivers a new Intent to THIS
 * existing instance via onNewIntent, rather than creating a second
 * Activity instance — required for the deep-link handling below to ever
 * see that second tap at all.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    /** Settings Phase 2: apply the in-app language choice (SYSTEM leaves the context untouched). */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.omidgame.mench.core.ui.theme.AppLanguageStore.wrap(newBase))
    }


    @Inject lateinit var callSessionManager: CallSessionManager

    private var pendingConversationId by mutableStateOf<String?>(null)
    /** Phase 1C: a resolved in-app route from mench:// links, app links or "share to MENCH". */
    private var pendingRoute by mutableStateOf<String?>(null)

    private fun handleDeepLink(intent: Intent?) {
        when (val target = com.omidgame.mench.feature.product.DeepLinks.resolve(intent)) {
            is com.omidgame.mench.feature.product.DeepLinkTarget.Chat -> pendingConversationId = target.conversationId
            is com.omidgame.mench.feature.product.DeepLinkTarget.Route -> pendingRoute = target.route
            null -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: swaps Theme.Mench.Splash for the post-splash theme.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingConversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        handleDeepLink(intent)

        // Phase 2B: while a call is ringing or running the call screen may appear over the lock screen and wake the
        // display (so an incoming call is answerable without unlocking first); it goes back to normal when the call ends.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                callSessionManager.state.collect { call -> showOverLockScreen(call !is CallUiState.Idle) }
            }
        }

        setContent {
            // Hilt-scoped to this Activity's lifecycle, not tied to any
            // one destination in MenchNavGraph — so switching a value on
            // the Appearance settings screen re-themes every screen
            // underneath it (including itself) with no navigation and no
            // process restart. See ThemeSettingsViewModel's doc comment.
            val themeSettingsViewModel: ThemeSettingsViewModel = hiltViewModel()
            val appearance by themeSettingsViewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (appearance.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Dates read the calendar / digit choice from here (idempotent assignment, cheap).
            com.omidgame.mench.core.ui.theme.MenchCalendar.configure(appearance.calendarStyle, appearance.digitStyle)

            // FLAG_SECURE on/off follows the Appearance switch live (Phase 4C screen security).
            LaunchedEffect(appearance.screenSecurity) {
                if (appearance.screenSecurity) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            MenchTheme(
                darkTheme = darkTheme,
                accentColor = appearance.accentColor,
                fontScale = appearance.fontScale,
                bubbleStyle = appearance.bubbleStyle,
                useDynamicColor = appearance.useDynamicColor,
                useAmoled = appearance.useAmoled,
                digitStyle = appearance.digitStyle,
            ) {
                val navController = rememberNavController()
                val appLockViewModel: AppLockGateViewModel = hiltViewModel()
                val isLocked by appLockViewModel.isLocked.collectAsStateWithLifecycle()
                val lockState by appLockViewModel.uiState.collectAsStateWithLifecycle()
                val biometricAuthenticator = remember { BiometricAuthenticator(this) }
                val coroutineScope = rememberCoroutineScope()

                // Requested once per process, right after the first frame
                // — not blocking, not repeated on every recomposition (the
                // launcher itself is stable across recompositions since it's
                // created once by rememberLauncherForActivityResult). A
                // denial here is a fully supported, non-degraded state (see
                // MenchFirebaseMessagingService's SecurityException catch) —
                // there is deliberately no re-prompt/rationale UI for this
                // in Phase 6; that belongs with the fuller Settings/
                // Notifications screen work, not bolted on here.
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* no-op either way — see doc comment above */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    appLockViewModel.onBiometricAvailabilityChecked(biometricAuthenticator.canAuthenticate())
                }

                Box {
                    MenchNavGraph(
                        navController = navController,
                        pendingConversationId = pendingConversationId,
                        onPendingConversationConsumed = { pendingConversationId = null },
                        pendingRoute = pendingRoute,
                        onPendingRouteConsumed = { pendingRoute = null },
                    )

                    if (isLocked) {
                        LockScreen(
                            state = lockState,
                            onSubmitPin = appLockViewModel::onSubmitPin,
                            onBiometricRequested = {
                                coroutineScope.launch {
                                    when (
                                        val result = biometricAuthenticator.authenticate(
                                            title = getString(R.string.applock_biometric_prompt_title),
                                            negativeButtonText = getString(R.string.applock_use_pin_instead),
                                        )
                                    ) {
                                        is BiometricResult.Success -> appLockViewModel.onBiometricSucceeded()
                                        is BiometricResult.Error -> appLockViewModel.onBiometricError(result.message)
                                        BiometricResult.Cancelled, BiometricResult.Unavailable -> Unit
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun showOverLockScreen(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enabled)
            setTurnScreenOn(enabled)
        } else {
            @Suppress("DEPRECATION")
            val flags = android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (enabled) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    /** Phase 2B: leaving the app during a video call shrinks it into a floating picture-in-picture window instead of hiding it. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val call = callSessionManager.state.value
        if (call is CallUiState.Active && call.session.callType == CallType.VIDEO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        CallPip.inPip.value = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { pendingConversationId = it }
        handleDeepLink(intent)
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversationId"
    }
}
