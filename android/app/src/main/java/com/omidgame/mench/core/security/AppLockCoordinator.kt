package com.omidgame.mench.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.omidgame.mench.feature.security.domain.AppLockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registered once, on [androidx.lifecycle.ProcessLifecycleOwner], in
 * MenchApplication.onCreate() — this observes the *process's* foreground/
 * background transitions, not any single Activity's, so it keeps working
 * correctly across configuration changes and is the single source of
 * truth for "is the lock screen currently shown" regardless of which
 * screen the user was on when the app was backgrounded (spec 32).
 *
 * MainActivity reads [isLocked] and overlays LockScreen on top of the nav
 * graph when true, rather than this class knowing anything about
 * navigation itself.
 */
@Singleton
class AppLockCoordinator @Inject constructor(
    private val appLockRepository: AppLockRepository,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    override fun onStop(owner: LifecycleOwner) {
        scope.launch {
            // Recording "now" only when app lock is actually on avoids
            // writing to encrypted prefs on every single background event
            // for users who never enabled the feature.
            if (appLockRepository.isAppLockEnabled()) {
                appLockRepository.noteActive()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { evaluateNow() }
    }

    /**
     * Same check as [onStart], exposed directly so [AppLockGateViewModel]
     * can run it the instant the root composable appears rather than
     * waiting on ProcessLifecycleOwner's own dispatch timing, which can
     * lag a frame or two behind first composition on cold start.
     */
    suspend fun evaluateNow() {
        if (appLockRepository.shouldLock()) {
            _isLocked.value = true
        }
    }

    fun markUnlocked() {
        scope.launch { appLockRepository.noteActive() }
        _isLocked.value = false
    }
}
