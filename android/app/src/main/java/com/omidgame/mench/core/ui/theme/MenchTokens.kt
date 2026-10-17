package com.omidgame.mench.core.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * MENCH design tokens (Phase 1A). One place for spacing, elevation, corner radii and
 * motion timing so feature code never invents magic numbers. Springs live next to the
 * Compose helpers in `core/ui/components/Motion.kt` (MenchMotion); this file holds the
 * numeric/duration/easing half of the same motion spec.
 */
object MenchSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val huge = 48.dp
}

object MenchElevation {
    val none = 0.dp
    val low = 1.dp
    val medium = 3.dp
    val high = 6.dp
    val overlay = 12.dp
}

object MenchRadius {
    val sm = 8.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 28.dp
    val pill = 999.dp
}

/** Durations in ms. Spec: keep UI motion between 150 and 350 ms. */
object MenchDuration {
    const val INSTANT = 90
    const val FAST = 150
    const val STANDARD = 250
    const val EMPHASIZED = 350
}

object MenchEasing {
    /** Default for most transitions (fast start, gentle landing). */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

/**
 * True when the user turned system animations off (Developer options / "Remove animations").
 * Decorative motion should be skipped when this is true.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
