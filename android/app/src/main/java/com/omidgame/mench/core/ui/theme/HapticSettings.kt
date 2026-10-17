package com.omidgame.mench.core.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext

/**
 * Phase 3C — the haptics map. Three independent switches, all on by default and stored on the phone only:
 *  - [KEY_TOUCH]: the small ticks for taps, long-presses, menus and the send button (everything that goes through Compose haptics);
 *  - [KEY_AFFECTION]: the vibration patterns of a hug, a kiss and "miss you";
 *  - heartbeat vibration lives with the effects settings ([com.omidgame.mench.feature.chat.presentation.MessageEffectSettings]).
 */
object HapticSettings {
    private const val PREFS = "haptic_settings"
    const val KEY_TOUCH = "touch_haptics"
    const val KEY_AFFECTION = "affection_vibration"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Boolean = prefs(context).getBoolean(key, true)

    fun set(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    internal fun prefsName(): String = PREFS
}

/** A haptic setting as Compose state; updates live when changed in Settings. */
@Composable
fun rememberHapticSetting(key: String): Boolean {
    val context = LocalContext.current
    var value by remember(key) { mutableStateOf(HapticSettings.get(context, key)) }
    DisposableEffect(context, key) {
        val prefs = context.applicationContext.getSharedPreferences(HapticSettings.prefsName(), Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = HapticSettings.get(context, key) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

/**
 * Wraps the platform haptics so every `LocalHapticFeedback.current.performHapticFeedback(...)` in the app obeys the
 * "touch feedback" switch without changing any call site.
 */
class GatedHapticFeedback(
    private val delegate: HapticFeedback,
    private val enabled: () -> Boolean,
) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (enabled()) delegate.performHapticFeedback(hapticFeedbackType)
    }
}
