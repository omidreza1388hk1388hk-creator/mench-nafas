package com.omidgame.mench.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = MenchAccentDark,
    background = MenchInk900,
    surface = MenchInk900,
    error = MenchError,
)

private val LightColors = lightColorScheme(
    primary = MenchAccent,
    background = MenchInk50,
    surface = MenchInk50,
    error = MenchError,
)

@Composable
fun MenchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MenchTypography,
        shapes = MenchShapes,
        content = content,
    )
}
