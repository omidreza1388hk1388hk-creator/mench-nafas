package com.omidgame.mench.feature.appearance

import com.omidgame.mench.feature.appearance.domain.AccentColor
import com.omidgame.mench.feature.appearance.domain.AppearanceSettings
import com.omidgame.mench.feature.appearance.domain.BubbleStyle
import com.omidgame.mench.feature.appearance.domain.ThemeMode
import com.omidgame.mench.feature.appearance.presentation.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetTest {
    @Test
    fun matchesWhenModeAccentAndBubbleAgree() {
        val p = ThemePreset.MIDNIGHT
        assertTrue(p.matches(AppearanceSettings(p.mode, p.accent, bubbleStyle = p.bubbleStyle)))
        assertFalse(p.matches(AppearanceSettings(ThemeMode.LIGHT, p.accent, bubbleStyle = p.bubbleStyle)))
        assertFalse(p.matches(AppearanceSettings(p.mode, AccentColor.RED, bubbleStyle = p.bubbleStyle)))
        assertFalse(p.matches(AppearanceSettings(p.mode, p.accent, bubbleStyle = BubbleStyle.SQUARED)))
    }

    @Test
    fun dynamicColorIsOffByDefault() {
        assertFalse(AppearanceSettings().useDynamicColor)
    }

    @Test
    fun everyPresetIsDistinct() {
        val keys = ThemePreset.entries.map { Triple(it.mode, it.accent, it.wallpaper) }
        assertEquals(keys.size, keys.toSet().size)
    }
}
