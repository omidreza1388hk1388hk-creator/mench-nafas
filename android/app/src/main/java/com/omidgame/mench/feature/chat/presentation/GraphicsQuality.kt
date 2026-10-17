package com.omidgame.mench.feature.chat.presentation

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Phase 3B — the graphics performance guardrail the user can control.
 * AUTO follows the device (half the particles on low-RAM phones), LOW saves battery (few particles, no living
 * backgrounds), HIGH always draws everything.
 */
enum class GraphicsQuality {
    AUTO,
    LOW,
    HIGH,
    ;

    /** Multiplier applied to every particle count. */
    fun particleFactor(isLowRamDevice: Boolean): Float = when (this) {
        AUTO -> if (isLowRamDevice) 0.5f else 1f
        LOW -> 0.35f
        HIGH -> 1f
    }

    /** Whether the moving wallpaper layers are drawn at all. */
    val allowsLivingBackground: Boolean get() = this != LOW

    companion object {
        private const val PREFS = "graphics_quality"
        private const val KEY = "quality"

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun get(context: Context): GraphicsQuality =
            runCatching { valueOf(prefs(context).getString(KEY, AUTO.name).orEmpty()) }.getOrDefault(AUTO)

        fun set(context: Context, quality: GraphicsQuality) {
            prefs(context).edit().putString(KEY, quality.name).apply()
        }

        fun isLowRam(context: Context): Boolean =
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
}

/**
 * Decides, from the average duration of the last frames, whether the animated wallpaper should pause itself.
 * Hysteresis: it pauses above [DEGRADE_ABOVE_MS] (about 40 fps) and resumes only below [RECOVER_BELOW_MS] (about 55 fps),
 * so it does not flicker on and off.
 */
internal const val DEGRADE_ABOVE_MS = 24.0
internal const val RECOVER_BELOW_MS = 18.0

internal fun shouldDegrade(averageFrameMs: Double, currentlyDegraded: Boolean): Boolean =
    if (currentlyDegraded) averageFrameMs > RECOVER_BELOW_MS else averageFrameMs > DEGRADE_ABOVE_MS

/** The saved quality as Compose state; updates live when changed from Settings. */
@Composable
fun rememberGraphicsQuality(): GraphicsQuality {
    val context = LocalContext.current
    var value by remember { mutableStateOf(GraphicsQuality.get(context)) }
    DisposableEffect(context) {
        val prefs = context.applicationContext.getSharedPreferences("graphics_quality", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = GraphicsQuality.get(context) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}
