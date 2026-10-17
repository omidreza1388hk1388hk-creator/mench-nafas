package com.omidgame.mench.core.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Gulzar (SIL OFL), a Nastaliq face, bundled in assets/fonts/. Used ONLY for the handwritten feel of
 * love letters and surprise messages; the rest of the app uses Vazirmatn. Falls back to the system
 * cursive family if the file is missing, so a missing asset never crashes.
 */
private fun loadGulzar(context: Context): FontFamily? = runCatching {
    val present = context.assets.list("fonts")?.toSet().orEmpty()
    if ("Gulzar-Arabic.ttf" in present) FontFamily(Font("fonts/Gulzar-Arabic.ttf", context.assets, FontWeight.Normal)) else null
}.getOrNull()

@Composable
fun rememberGulzarFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context) { loadGulzar(context) ?: FontFamily.Cursive }
}
