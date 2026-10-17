package com.omidgame.mench.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.omidgame.mench.core.ui.a11y.MenchA11y
import com.omidgame.mench.feature.appearance.domain.DigitStyle
import com.omidgame.mench.feature.appearance.domain.AccentColor
import com.omidgame.mench.feature.appearance.domain.BubbleStyle
import com.omidgame.mench.feature.appearance.domain.FontScale

// WCAG AA aware (Phase 4A): picks white or dark ink by real contrast, not a luminance cut-off.
private fun readableOn(color: Color): Color = MenchA11y.readableOn(color)

/**
 * Builds the scheme around the accent instead of only setting `primary`:
 * the background, surfaces, received-message bubbles (surfaceVariant) and
 * containers all pick up a faint tint of the accent, so a rose accent
 * makes the whole app feel rose rather than just recoloring buttons.
 * Text on the accent is chosen by luminance so it stays readable on every
 * accent in both modes.
 */
private fun schemeFor(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = readableOn(accent)
    val gold = if (dark) MenchGoldDark else MenchGoldLight
    return if (dark) {
        val bg = lerp(MenchPlum950, accent, 0.04f)
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(bg, accent, 0.30f),
            onPrimaryContainer = Color.White,
            secondary = lerp(accent, Color.White, 0.25f),
            tertiary = gold,
            onTertiary = Color(0xFF2A1D00),
            tertiaryContainer = lerp(bg, gold, 0.22f),
            onTertiaryContainer = Color(0xFFFFEFC9),
            background = bg,
            onBackground = Color(0xFFF3E9EE),
            surface = bg,
            onSurface = Color(0xFFF3E9EE),
            surfaceVariant = lerp(MenchPlum900, accent, 0.14f),
            onSurfaceVariant = Color(0xFFE9DDE3),
            surfaceContainerLowest = lerp(MenchPlum950, Color.Black, 0.3f),
            surfaceContainerLow = lerp(bg, Color.White, 0.025f),
            surfaceContainer = lerp(bg, Color.White, 0.05f),
            surfaceContainerHigh = lerp(bg, Color.White, 0.08f),
            surfaceContainerHighest = lerp(bg, Color.White, 0.11f),
            outline = lerp(bg, Color.White, 0.38f),
            outlineVariant = lerp(bg, Color.White, 0.14f),
            error = MenchError,
        )
    } else {
        val bg = lerp(MenchCream50, accent, 0.025f)
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(Color.White, accent, 0.16f),
            onPrimaryContainer = Color(0xFF2A0A17),
            secondary = lerp(accent, Color.Black, 0.15f),
            tertiary = gold,
            onTertiary = Color.White,
            tertiaryContainer = lerp(Color.White, gold, 0.20f),
            onTertiaryContainer = Color(0xFF3A2800),
            background = bg,
            onBackground = Color(0xFF241A1F),
            surface = bg,
            onSurface = Color(0xFF241A1F),
            surfaceVariant = lerp(Color(0xFFF3E9ED), accent, 0.10f),
            onSurfaceVariant = Color(0xFF2E2329),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(bg, accent, 0.02f),
            surfaceContainer = lerp(bg, accent, 0.045f),
            surfaceContainerHigh = lerp(bg, accent, 0.07f),
            surfaceContainerHighest = lerp(bg, accent, 0.10f),
            outline = lerp(bg, Color.Black, 0.45f),
            outlineVariant = lerp(bg, accent, 0.18f),
            error = MenchError,
        )
    }
}

/** Pure-black variant of a dark scheme: backgrounds and surfaces go to #000, raised surfaces keep a hair of lift so cards stay readable. */
internal fun amoledScheme(base: ColorScheme): ColorScheme = base.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, Color.White, 0.03f),
    surfaceContainer = lerp(Color.Black, Color.White, 0.055f),
    surfaceContainerHigh = lerp(Color.Black, Color.White, 0.085f),
    surfaceContainerHighest = lerp(Color.Black, Color.White, 0.115f),
)

/** Latest value of the touch-haptics switch, read by [GatedHapticFeedback] at the moment a haptic is requested. */
private val currentTouchHaptics = java.util.concurrent.atomic.AtomicBoolean(true)

/** The scheme a preset or accent would produce, for live previews that must not touch the app-wide theme. */
fun previewColorScheme(accent: AccentColor, dark: Boolean): ColorScheme = schemeFor(accent.swatch(dark), dark)

/**
 * Cross-dissolves between colour schemes (about 350 ms) so changing theme or preset fades instead of snapping.
 * With "remove animations" the change is immediate.
 */
@Composable
private fun animatedColorScheme(target: ColorScheme, reduceMotion: Boolean): ColorScheme {
    val spec = if (reduceMotion) tween<Color>(0) else tween<Color>(350)
    @Composable
    fun a(c: Color): Color = animateColorAsState(c, spec, label = "scheme").value
    return target.copy(
        primary = a(target.primary),
        onPrimary = a(target.onPrimary),
        primaryContainer = a(target.primaryContainer),
        onPrimaryContainer = a(target.onPrimaryContainer),
        secondary = a(target.secondary),
        tertiary = a(target.tertiary),
        tertiaryContainer = a(target.tertiaryContainer),
        background = a(target.background),
        onBackground = a(target.onBackground),
        surface = a(target.surface),
        onSurface = a(target.onSurface),
        surfaceVariant = a(target.surfaceVariant),
        onSurfaceVariant = a(target.onSurfaceVariant),
        surfaceContainerLow = a(target.surfaceContainerLow),
        surfaceContainer = a(target.surfaceContainer),
        surfaceContainerHigh = a(target.surfaceContainerHigh),
        surfaceContainerHighest = a(target.surfaceContainerHighest),
        outline = a(target.outline),
        outlineVariant = a(target.outlineVariant),
    )
}

/**
 * Every parameter maps 1:1 to a control on the Appearance settings screen.
 * Defaults describe a fresh install (system theme, ROSE accent, medium
 * type, rounded bubbles).
 */
@Composable
fun MenchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.ROSE,
    fontScale: FontScale = FontScale.MEDIUM,
    bubbleStyle: BubbleStyle = BubbleStyle.ROUNDED,
    useDynamicColor: Boolean = false,
    useAmoled: Boolean = false,
    digitStyle: DigitStyle = DigitStyle.AUTO,
    content: @Composable () -> Unit,
) {
    val accent = accentColor.swatch(darkTheme)
    val context = androidx.compose.ui.platform.LocalContext.current
    val dynamic = useDynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val target = if (dynamic) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(darkTheme, accent) { schemeFor(accent, darkTheme) }
    }
    val shown = if (darkTheme && useAmoled) amoledScheme(target) else target
    val colors = animatedColorScheme(shown, rememberReduceMotion())
    val fontFamily = rememberMenchFontFamily()
    val mediumWeight = rememberMediumTextWeight()
    val typography = remember(fontScale, fontFamily, mediumWeight) { menchTypography(fontScale.multiplier, fontFamily, mediumWeight) }
    val shapes = remember(bubbleStyle) { menchShapes(bubbleStyle) }

    // AUTO follows the app language; PERSIAN / LATIN are the explicit user choice (Phase 4A).
    val persianDigits = remember(digitStyle) {
        when (digitStyle) {
            DigitStyle.PERSIAN -> true
            DigitStyle.LATIN -> false
            DigitStyle.AUTO -> java.util.Locale.getDefault().language == "fa"
        }
    }
    val touchHaptics = rememberHapticSetting(HapticSettings.KEY_TOUCH)
    val platformHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val haptics = remember(platformHaptics) { GatedHapticFeedback(platformHaptics) { currentTouchHaptics.get() } }
    currentTouchHaptics.set(touchHaptics)
    CompositionLocalProvider(
        LocalPersianDigits provides persianDigits,
        androidx.compose.ui.platform.LocalHapticFeedback provides haptics,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

/** Settings Phase 2: true when the user chose the medium text weight (prefs `mench_text_weight`). Recomposes on change. */
@Composable
fun rememberMediumTextWeight(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.applicationContext.getSharedPreferences("mench_text_weight", android.content.Context.MODE_PRIVATE) }
    var medium by remember { androidx.compose.runtime.mutableStateOf(prefs.getString("weight", null) == "medium") }
    androidx.compose.runtime.DisposableEffect(prefs) {
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, _ -> medium = p.getString("weight", null) == "medium" }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return medium
}
