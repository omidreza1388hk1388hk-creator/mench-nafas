package com.omidgame.mench.feature.settings.presentation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Handles registry rows whose target is a [com.omidgame.mench.feature.settings.domain.SettingsTarget.Action]:
 * in-place dialogs and system intents that used to live inside the flat SettingsScreen. [content] receives a
 * `run(actionId)` function; unknown ids are ignored.
 */
@Composable
fun SettingsActionHost(content: @Composable (run: (String) -> Unit) -> Unit) {
    val context = LocalContext.current
    var showWallpaper by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    if (showWallpaper) {
        com.omidgame.mench.feature.chat.presentation.WallpaperDialog(onDismiss = { showWallpaper = false })
    }
    if (showEffects) {
        com.omidgame.mench.feature.chat.presentation.MessageEffectsSettingsDialog(onDismiss = { showEffects = false })
    }
    content { id ->
        when (id) {
            "wallpaper" -> showWallpaper = true
            "effects" -> showEffects = true
            "battery" -> runCatching {
                // Exempting the app from battery optimisation keeps the background connection alive on
                // aggressive vendors (Xiaomi, Huawei...). Same intent the flat list used.
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + context.packageName),
                    ),
                )
            }
        }
    }
}
