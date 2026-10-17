package com.omidgame.mench.feature.appearance.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.previewColorScheme
import com.omidgame.mench.feature.appearance.domain.AccentColor
import com.omidgame.mench.feature.appearance.domain.AppearanceSettings
import com.omidgame.mench.feature.appearance.domain.BubbleStyle
import com.omidgame.mench.feature.appearance.domain.ThemeMode
import com.omidgame.mench.feature.chat.presentation.ChatWallpaper

/** Phase 3C — curated themes: one tap sets light/dark, accent, bubble shape and the chat wallpaper together. */
enum class ThemePreset(
    val labelRes: Int,
    val mode: ThemeMode,
    val accent: AccentColor,
    val bubbleStyle: BubbleStyle,
    val wallpaper: ChatWallpaper,
    /** True-black dark variant (only meaningful with a dark mode). */
    val amoled: Boolean = false,
) {
    ROSE_EVENING(R.string.preset_rose_evening, ThemeMode.DARK, AccentColor.ROSE, BubbleStyle.ROUNDED, ChatWallpaper.SUNSET),
    MIDNIGHT(R.string.preset_midnight, ThemeMode.DARK, AccentColor.INDIGO, BubbleStyle.ROUNDED, ChatWallpaper.STARRY),
    MINT_MORNING(R.string.preset_mint_morning, ThemeMode.LIGHT, AccentColor.GREEN, BubbleStyle.ROUNDED, ChatWallpaper.MINT),
    LAVENDER_DREAM(R.string.preset_lavender, ThemeMode.LIGHT, AccentColor.PINK, BubbleStyle.ROUNDED, ChatWallpaper.LAVENDER),
    OCEAN_AURORA(R.string.preset_aurora, ThemeMode.DARK, AccentColor.BLUE, BubbleStyle.ROUNDED, ChatWallpaper.AURORA),
    SOFT_BUBBLES(R.string.preset_bubbles, ThemeMode.LIGHT, AccentColor.ORANGE, BubbleStyle.ROUNDED, ChatWallpaper.BUBBLES),
    CLASSIC_SQUARE(R.string.preset_classic, ThemeMode.SYSTEM, AccentColor.BLUE, BubbleStyle.SQUARED, ChatWallpaper.PLAIN),
    OCEAN(R.string.preset_ocean, ThemeMode.DARK, AccentColor.TEAL, BubbleStyle.PILL, ChatWallpaper.AURORA),
    FOREST(R.string.preset_forest, ThemeMode.DARK, AccentColor.GREEN, BubbleStyle.LEAF, ChatWallpaper.MINT),
    GOLD_NIGHT(R.string.preset_gold, ThemeMode.DARK, AccentColor.GOLD, BubbleStyle.ROUNDED, ChatWallpaper.STARRY, amoled = true),
    MONO(R.string.preset_mono, ThemeMode.DARK, AccentColor.MONO, BubbleStyle.SQUARED, ChatWallpaper.PLAIN, amoled = true),
    ;

    /** True when [settings] already match this preset's mode, accent and bubble shape (the wallpaper is stored elsewhere). */
    fun matches(settings: AppearanceSettings): Boolean =
        settings.themeMode == mode && settings.accentColor == accent && settings.bubbleStyle == bubbleStyle && settings.useAmoled == amoled
}

/**
 * A horizontally scrolling row of preset cards. Each card previews its own theme on the spot (colours, wallpaper tint
 * and two sample bubbles) by wrapping itself in a MaterialTheme built from the preset, so what you see is what you get.
 */
@Composable
fun ThemePresetRow(settings: AppearanceSettings, systemDark: Boolean, onPick: (ThemePreset) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(ThemePreset.entries) { preset ->
            val dark = when (preset.mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            val scheme = remember(preset, dark) { previewColorScheme(preset.accent, dark) }
            val selected = preset.matches(settings)
            MaterialTheme(colorScheme = scheme) {
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(preset.wallpaper.brush(scheme.background, scheme.primary))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) scheme.primary else scheme.outlineVariant,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable { onPick(preset) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(preset.labelRes), style = MaterialTheme.typography.labelLarge, color = scheme.onBackground)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Start)
                            .clip(RoundedCornerShape(if (preset.bubbleStyle == BubbleStyle.SQUARED) 4.dp else 12.dp))
                            .background(scheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("•••", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(if (preset.bubbleStyle == BubbleStyle.SQUARED) 4.dp else 12.dp))
                            .background(scheme.primary)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("♥", color = scheme.onPrimary, style = MaterialTheme.typography.labelMedium) }
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp))
                }
            }
        }
    }
}
