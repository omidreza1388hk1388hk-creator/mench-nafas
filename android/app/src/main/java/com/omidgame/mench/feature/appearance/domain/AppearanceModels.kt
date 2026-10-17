package com.omidgame.mench.feature.appearance.domain

/**
 * Whether the app follows the OS light/dark setting or is pinned to one
 * of the two explicitly. SYSTEM is the default so a fresh install matches
 * platform behavior until the user opts into something else.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    val wireValue: String get() = name.lowercase()

    companion object {
        fun fromWireValue(value: String?): ThemeMode =
            entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

/**
 * Named accent identities only — no android.graphics.Color reference
 * here, so this stays a plain platform-agnostic domain type. The actual
 * Color values for each entry live in core/ui/theme/Color.kt, which is
 * allowed to depend on Compose; this module intentionally isn't.
 */
enum class AccentColor {
    /** Romantic rose — the default for fresh installs; existing users keep whatever they already picked. */
    ROSE,
    INDIGO,
    BLUE,
    GREEN,
    ORANGE,
    PINK,
    RED,

    /** Phase 3C: deep teal for the Ocean theme. */
    TEAL,

    /** Phase 3C: champagne gold. */
    GOLD,

    /** Phase 3C: neutral graphite for the Mono theme. */
    MONO,
    ;

    val wireValue: String get() = name.lowercase()

    companion object {
        fun fromWireValue(value: String?): AccentColor =
            entries.firstOrNull { it.wireValue == value } ?: ROSE
    }
}

/**
 * A multiplier applied to every type-ramp size in [com.omidgame.mench.core.ui.theme.menchTypography] —
 * a single scale factor rather than per-style overrides, so the type
 * ramp's relative proportions (title vs body vs label) are preserved at
 * every setting.
 */
enum class FontScale(val multiplier: Float) {
    SMALL(0.9f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
    ;

    val wireValue: String get() = name.lowercase()

    companion object {
        fun fromWireValue(value: String?): FontScale =
            entries.firstOrNull { it.wireValue == value } ?: MEDIUM
    }
}

/** Corner rounding family used for chat bubbles and message-adjacent chrome. */
enum class BubbleStyle {
    ROUNDED,
    SQUARED,

    /** Phase 3C: very round, capsule-like bubbles. */
    PILL,

    /** Phase 3C: three round corners and one sharper one, like a speech leaf. */
    LEAF,
    ;

    val wireValue: String get() = name.lowercase()

    companion object {
        fun fromWireValue(value: String?): BubbleStyle =
            entries.firstOrNull { it.wireValue == value } ?: ROUNDED
    }
}

/** How numbers are drawn: follow the app language, or force Persian / Latin digits. */
enum class DigitStyle {
    AUTO,
    PERSIAN,
    LATIN,
    ;

    val wireValue: String get() = name.lowercase()

    companion object {
        fun fromWireValue(value: String?): DigitStyle =
            entries.firstOrNull { it.wireValue == value } ?: AUTO
    }
}

/** Which calendar dates are shown in. Jalali is the default (the app is Persian-first). */
enum class CalendarStyle {
    JALALI,
    GREGORIAN,
    ;

    val wireValue: String get() = name.lowercase()

    companion object {
        fun fromWireValue(value: String?): CalendarStyle =
            entries.firstOrNull { it.wireValue == value } ?: JALALI
    }
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.ROSE,
    val fontScale: FontScale = FontScale.MEDIUM,
    val bubbleStyle: BubbleStyle = BubbleStyle.ROUNDED,
    /** Phase 3C: Material You — take the colours from the phone's wallpaper (Android 12+). Off by default. */
    val useDynamicColor: Boolean = false,
    /** Phase 3C: true-black dark theme (saves battery on OLED screens). Only has an effect in dark mode. */
    val useAmoled: Boolean = false,
    val digitStyle: DigitStyle = DigitStyle.AUTO,
    val calendarStyle: CalendarStyle = CalendarStyle.JALALI,
    /** FLAG_SECURE: blocks screenshots / screen recording and blanks the recents thumbnail. */
    val screenSecurity: Boolean = false,
)
