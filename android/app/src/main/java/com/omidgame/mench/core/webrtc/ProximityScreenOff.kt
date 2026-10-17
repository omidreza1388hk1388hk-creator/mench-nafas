package com.omidgame.mench.core.webrtc

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 2B — turns the screen off while the phone is held at the ear during a voice call (and back on when it is moved away). */
@Singleton
class ProximityScreenOff @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (wakeLock?.isHeld == true) return
            if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "mench:call-proximity").apply {
                setReferenceCounted(false)
                acquire(MAX_HOLD_MS) // safety net: released explicitly when the call ends
            }
        } else {
            runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
            wakeLock = null
        }
    }

    private companion object {
        const val MAX_HOLD_MS = 4L * 60 * 60 * 1000
    }
}
