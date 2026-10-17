package com.omidgame.mench.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.omidgame.mench.feature.appearance.domain.AccentColor

// Neutral ink tokens — shared by every accent palette below.
val MenchInk900 = Color(0xFF0E0E12)
val MenchInk50 = Color(0xFFF7F7FA)
val MenchError = Color(0xFFE5484D)

// Indigo — the original MENCH seed accent, still the default.
val MenchAccent = Color(0xFF5B6CFF)
val MenchAccentDark = Color(0xFF8891FF)

// Additional accent identities for the appearance picker. Each has a
// light-mode value (used on MenchInk50 surfaces) and a dark-mode value
// (used on MenchInk900 surfaces) tuned for roughly matching contrast.
val MenchBlueLight = Color(0xFF1565C0)
val MenchBlueDark = Color(0xFF82B1FF)

val MenchGreenLight = Color(0xFF2E7D32)
val MenchGreenDark = Color(0xFF81C784)

val MenchOrangeLight = Color(0xFFE65100)
val MenchOrangeDark = Color(0xFFFFB74D)

val MenchPinkLight = Color(0xFFAD1457)
val MenchPinkDark = Color(0xFFF48FB1)

val MenchRoseLight = Color(0xFFD62B6A)
val MenchRoseDark = Color(0xFFFF7FA8)

val MenchRedLight = Color(0xFFC62828)
val MenchRedDark = Color(0xFFEF9A9A)

/** The (light, dark) color pair an [AccentColor] resolves to. */
data class AccentPaletteColors(val light: Color, val dark: Color)

fun AccentColor.paletteColors(): AccentPaletteColors = when (this) {
    AccentColor.ROSE -> AccentPaletteColors(MenchRoseLight, MenchRoseDark)
    AccentColor.INDIGO -> AccentPaletteColors(MenchAccent, MenchAccentDark)
    AccentColor.BLUE -> AccentPaletteColors(MenchBlueLight, MenchBlueDark)
    AccentColor.GREEN -> AccentPaletteColors(MenchGreenLight, MenchGreenDark)
    AccentColor.ORANGE -> AccentPaletteColors(MenchOrangeLight, MenchOrangeDark)
    AccentColor.PINK -> AccentPaletteColors(MenchPinkLight, MenchPinkDark)
    AccentColor.RED -> AccentPaletteColors(MenchRedLight, MenchRedDark)
    AccentColor.TEAL -> AccentPaletteColors(Color(0xFF00796B), Color(0xFF4DB6AC))
    AccentColor.GOLD -> AccentPaletteColors(Color(0xFF9A6B00), Color(0xFFFFCA55))
    AccentColor.MONO -> AccentPaletteColors(Color(0xFF3A3A40), Color(0xFFCFCFD6))
}

/** The single resolved swatch to render for this accent, given whether we're in dark mode. */
fun AccentColor.swatch(darkTheme: Boolean): Color {
    val colors = paletteColors()
    return if (darkTheme) colors.dark else colors.light
}

// Warm champagne-gold: the second brand colour (tertiary). Used sparingly for
// anniversaries, badges, streaks and other "special" moments.
val MenchGoldLight = Color(0xFFB8860B)
val MenchGoldDark = Color(0xFFFFD27A)

// Warm, slightly plum-tinted neutrals so dark mode feels intimate, not cold blue-black.
val MenchPlum950 = Color(0xFF110A10)
val MenchPlum900 = Color(0xFF181017)
val MenchCream50 = Color(0xFFFFF9FA)
