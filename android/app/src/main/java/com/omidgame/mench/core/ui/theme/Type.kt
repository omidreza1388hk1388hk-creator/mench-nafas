package com.omidgame.mench.core.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private const val TITLE_LARGE_BASE_SP = 22f
private const val BODY_LARGE_BASE_SP = 16f
private const val BODY_MEDIUM_BASE_SP = 14f
private const val LABEL_LARGE_BASE_SP = 14f

// Persian/Arabic script has tall ascenders and stacked dots; the Latin-tuned Material line heights
// clip or crowd it. A flat 1.55 ratio keeps multi-line Persian paragraphs airy and even.
private const val PERSIAN_LINE_HEIGHT_RATIO = 1.55f

/**
 * Loads Vazirmatn from app/src/main/assets/fonts/ if the TTFs are bundled
 * (Vazirmatn-Regular/Medium/SemiBold/Bold.ttf) — works offline, which matters
 * because the Google-Fonts provider in Font.kt needs Play Services AND access
 * to Google, neither of which is dependable in Iran. Returns null when no
 * file is there, so the app falls back to [MenchFontFamily] exactly as before.
 */
private fun loadBundledFontFamily(context: Context): FontFamily? = runCatching {
    val assets = context.assets
    val present = assets.list("fonts")?.toSet().orEmpty()
    fun font(file: String, weight: FontWeight) =
        if (file in present) androidx.compose.ui.text.font.Font("fonts/$file", assets, weight) else null
    val fonts = listOfNotNull(
        font("Vazirmatn-Light.ttf", FontWeight.Light),
        font("Vazirmatn-Regular.ttf", FontWeight.Normal),
        font("Vazirmatn-Medium.ttf", FontWeight.Medium),
        font("Vazirmatn-SemiBold.ttf", FontWeight.SemiBold),
        font("Vazirmatn-Bold.ttf", FontWeight.Bold),
        font("Vazirmatn-ExtraBold.ttf", FontWeight.ExtraBold),
    )
    if (fonts.isEmpty()) null else FontFamily(fonts)
}.getOrNull()

@Composable
fun rememberMenchFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context) { loadBundledFontFamily(context) ?: MenchFontFamily }
}

private fun TextUnit.scaled(scale: Float): TextUnit = if (this == TextUnit.Unspecified) this else this * scale

/**
 * The FULL Material3 ramp (all 15 styles), each with the Persian font and
 * the user's font-size [scale]. Before, only 4 styles had the custom font,
 * so anything using titleSmall / titleMedium / headlineSmall / labelMedium
 * (the heart, letters, memories and mood UI included) silently rendered in Roboto.
 */
fun menchTypography(scale: Float = 1.0f, family: FontFamily = MenchFontFamily, mediumWeight: Boolean = false): Typography {
    val base = Typography()
    fun TextStyle.styled(sp: Float? = null): TextStyle {
        val sizeSp = (sp ?: (if (fontSize == TextUnit.Unspecified) 14f else fontSize.value)) * scale
        return copy(
            fontFamily = family,
            // Settings Phase 2: "medium" text weight lifts only styles that are plain Normal; bold titles stay as designed.
            fontWeight = if (mediumWeight && (fontWeight == null || fontWeight == FontWeight.Normal)) FontWeight.Medium else fontWeight,
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * PERSIAN_LINE_HEIGHT_RATIO).sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
            // Balanced, paragraph-quality wrapping instead of greedy: fewer one-word last lines in Persian text.
            lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
        )
    }
    return Typography(
        displayLarge = base.displayLarge.styled(),
        displayMedium = base.displayMedium.styled(),
        displaySmall = base.displaySmall.styled(),
        headlineLarge = base.headlineLarge.styled(),
        headlineMedium = base.headlineMedium.styled(),
        headlineSmall = base.headlineSmall.styled(),
        titleLarge = base.titleLarge.styled(TITLE_LARGE_BASE_SP),
        titleMedium = base.titleMedium.styled(),
        titleSmall = base.titleSmall.styled(),
        bodyLarge = base.bodyLarge.styled(BODY_LARGE_BASE_SP),
        bodyMedium = base.bodyMedium.styled(BODY_MEDIUM_BASE_SP),
        bodySmall = base.bodySmall.styled(),
        labelLarge = base.labelLarge.styled(LABEL_LARGE_BASE_SP),
        labelMedium = base.labelMedium.styled(),
        labelSmall = base.labelSmall.styled(),
    )
}

// Preserved for any call site still referencing the old fixed value directly.
val MenchTypography: Typography = menchTypography()
