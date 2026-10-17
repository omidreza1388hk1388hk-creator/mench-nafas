package com.omidgame.mench.core.webrtc

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.omidgame.mench.feature.settings.domain.CallQualityPreset
import com.omidgame.mench.feature.settings.domain.CallSettingsSnapshot

/**
 * Storage for call settings (plain prefs). [current] is an in-memory copy so the WebRTC code, which has no
 * Context at the point of use, can read it; it is loaded in Application.onCreate and refreshed on every write.
 */
object CallSettingsStore {
    private const val PREFS = "mench_call_settings"

    @Volatile var current: CallSettingsSnapshot = CallSettingsSnapshot()
        private set

    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): CallSettingsSnapshot {
        val p = prefs(context)
        return CallSettingsSnapshot(
            qualityPreset = CallQualityPreset.fromStorage(p.getString("quality", null)),
            noiseSuppression = p.getBoolean("noise_suppression", true),
            echoCancellation = p.getBoolean("echo_cancellation", true),
            voiceStartsOnSpeaker = p.getBoolean("voice_speaker", false),
            frontCameraDefault = p.getBoolean("front_camera", true),
            mirrorFrontCamera = p.getBoolean("mirror_front", true),
        ).also { current = it }
    }

    private fun update(c: Context, block: SharedPreferences.Editor.() -> Unit) {
        prefs(c).edit().apply(block).apply()
        load(c)
    }

    fun setQuality(c: Context, v: CallQualityPreset) = update(c) { putString("quality", v.storageValue) }
    fun setNoiseSuppression(c: Context, v: Boolean) = update(c) { putBoolean("noise_suppression", v) }
    fun setEchoCancellation(c: Context, v: Boolean) = update(c) { putBoolean("echo_cancellation", v) }
    fun setVoiceSpeaker(c: Context, v: Boolean) = update(c) { putBoolean("voice_speaker", v) }
    fun setFrontCamera(c: Context, v: Boolean) = update(c) { putBoolean("front_camera", v) }
    fun setMirrorFront(c: Context, v: Boolean) = update(c) { putBoolean("mirror_front", v) }
}

@Composable
fun rememberCallSettings(): CallSettingsSnapshot {
    val context = LocalContext.current
    var snap by remember { mutableStateOf(CallSettingsStore.load(context)) }
    DisposableEffect(context) {
        val p = context.applicationContext.getSharedPreferences("mench_call_settings", Context.MODE_PRIVATE)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> snap = CallSettingsStore.load(context) }
        p.registerOnSharedPreferenceChangeListener(l)
        onDispose { p.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return snap
}

/** What the last call's quality monitor measured, kept in memory for the diagnostics page (no call content). */
data class CallDiagnosticsSnapshot(
    val callType: String,
    val rttMs: Double?,
    val packetLossPercent: Double?,
    val jitterMs: Double?,
    val tier: String,
    val bitrateCapBps: Int?,
    val preset: String,
    val recordedAtMillis: Long,
)

object CallDiagnostics {
    @Volatile var last: CallDiagnosticsSnapshot? = null
}
