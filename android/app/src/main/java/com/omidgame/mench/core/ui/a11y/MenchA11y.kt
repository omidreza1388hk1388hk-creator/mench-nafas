package com.omidgame.mench.core.ui.a11y

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Accessibility helpers shared by every screen (Phase 4A).
 * Pure functions where possible so they can be unit-tested without Compose.
 */
object MenchA11y {
    /** Android's recommended minimum touch target. */
    val MinTouchTarget = 48.dp

    /** WCAG 2.x AA for normal-size text. */
    const val AA_NORMAL_TEXT = 4.5f

    /** WCAG relative-luminance contrast ratio, 1.0 (identical) .. 21.0 (black on white). */
    fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /**
     * Text color for content drawn on [background]. White is preferred (brand look);
     * if white cannot reach AA we switch to the dark ink, and if neither reaches AA
     * the better of the two wins.
     */
    fun readableOn(
        background: Color,
        light: Color = Color.White,
        dark: Color = Color(0xFF1B1B1F),
    ): Color {
        val lightRatio = contrastRatio(light, background)
        if (lightRatio >= AA_NORMAL_TEXT) return light
        val darkRatio = contrastRatio(dark, background)
        return if (darkRatio >= AA_NORMAL_TEXT || darkRatio > lightRatio) dark else light
    }
}

/** Grows small tappable elements to the 48 dp minimum without shrinking larger ones. */
fun Modifier.minTouchTarget(): Modifier =
    this.defaultMinSize(minWidth = MenchA11y.MinTouchTarget, minHeight = MenchA11y.MinTouchTarget)
