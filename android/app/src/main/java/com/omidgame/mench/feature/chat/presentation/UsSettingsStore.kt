package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.omidgame.mench.feature.settings.domain.AffectionHaptics
import com.omidgame.mench.feature.settings.domain.HapticStrength
import com.omidgame.mench.feature.settings.domain.UsSettingsSnapshot

/** Storage for the couple-feature settings (plain prefs). Same pattern as [ChatSettingsStore]. */
object UsSettingsStore {
    private const val PREFS = "mench_us_settings"

    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): UsSettingsSnapshot {
        val p = prefs(context)
        return UsSettingsSnapshot(
            moodEnabled = p.getBoolean("mood", true),
            questionEnabled = p.getBoolean("question", true),
            wishlistEnabled = p.getBoolean("wishlist", true),
            memoriesEnabled = p.getBoolean("memories", true),
            hapticStrength = HapticStrength.fromStorage(p.getString("haptic_strength", null)),
            heartsAnimation = p.getBoolean("hearts_animation", true),
        )
    }

    private fun update(c: Context, block: SharedPreferences.Editor.() -> Unit) = prefs(c).edit().apply(block).apply()

    fun setMood(c: Context, v: Boolean) = update(c) { putBoolean("mood", v) }
    fun setQuestion(c: Context, v: Boolean) = update(c) { putBoolean("question", v) }
    fun setWishlist(c: Context, v: Boolean) = update(c) { putBoolean("wishlist", v) }
    fun setMemories(c: Context, v: Boolean) = update(c) { putBoolean("memories", v) }
    fun setHapticStrength(c: Context, v: HapticStrength) = update(c) { putString("haptic_strength", v.storageValue) }
    fun setHeartsAnimation(c: Context, v: Boolean) = update(c) { putBoolean("hearts_animation", v) }

    /**
     * The vibration for an affection pattern, scaled by the user's strength. NORMAL (and devices without amplitude
     * control) get the original timing-only waveform, so default behaviour is exactly as before.
     */
    fun effectFor(context: Context, vibrator: Vibrator, timings: LongArray): VibrationEffect {
        val amplitudes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
            AffectionHaptics.amplitudes(timings.size, load(context).hapticStrength)
        } else {
            null
        }
        return if (amplitudes != null) VibrationEffect.createWaveform(timings, amplitudes, -1) else VibrationEffect.createWaveform(timings, -1)
    }
}

@Composable
fun rememberUsSettings(): UsSettingsSnapshot {
    val context = LocalContext.current
    var snap by remember { mutableStateOf(UsSettingsStore.load(context)) }
    DisposableEffect(context) {
        val p = context.applicationContext.getSharedPreferences("mench_us_settings", Context.MODE_PRIVATE)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> snap = UsSettingsStore.load(context) }
        p.registerOnSharedPreferenceChangeListener(l)
        onDispose { p.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return snap
}
