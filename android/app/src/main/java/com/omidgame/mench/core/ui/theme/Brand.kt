package com.omidgame.mench.core.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Brand-level visual tokens that Material's ColorScheme has no slot for.
 * Everything derives from the current accent, so every accent choice gets the
 * same polished look (gradients, glow, glass) instead of only recoloured flat fills.
 */
object MenchBrand {

    /** Hero gradient: top bars, primary buttons, own message bubbles, splash. */
    @Composable
    fun heroBrush(): Brush {
        val a = MaterialTheme.colorScheme.primary
        return Brush.linearGradient(listOf(lerp(a, Color.White, 0.14f), a, lerp(a, Color.Black, 0.28f)))
    }

    /** Own-message bubble gradient. */
    @Composable
    fun bubbleBrush(): Brush {
        val a = MaterialTheme.colorScheme.primary
        return Brush.linearGradient(listOf(lerp(a, Color.White, 0.08f), lerp(a, Color.Black, 0.24f)))
    }

    /** Soft ambient glow, used behind avatars, call screen and special cards. */
    @Composable
    fun glowBrush(): Brush {
        val a = MaterialTheme.colorScheme.primary
        return Brush.radialGradient(listOf(a.copy(alpha = 0.35f), Color.Transparent))
    }

    /** Frosted-glass fill for floating bars (composer, top bars over wallpaper). */
    @Composable
    fun glassFill(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

    /** Hairline border that makes glass and cards feel crisp. */
    @Composable
    fun hairline(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
}

/** Transparent top-bar colours with on-primary content; pair with Modifier.background(MenchBrand.heroBrush()). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun menchTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)
