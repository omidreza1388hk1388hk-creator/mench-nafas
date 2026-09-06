package com.omidgame.mench

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    private var pendingConversationId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingConversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)

        setContent {
            MenchTheme(darkTheme = isSystemInDarkTheme()) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { pendingConversationId = it }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversationId"
    }
}
