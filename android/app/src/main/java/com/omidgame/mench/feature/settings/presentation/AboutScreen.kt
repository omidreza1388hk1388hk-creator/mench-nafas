package com.omidgame.mench.feature.settings.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omidgame.mench.BuildConfig
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.settings.SettingsRow
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection

/** Third-party material the app ships, with the licence each one is under. Names are proper nouns, so not translated. */
private val LICENSES = listOf(
    "Kenney Interface Sounds" to "CC0 1.0",
    "Vazirmatn font" to "SIL Open Font License 1.1",
    "Gulzar font" to "SIL Open Font License 1.1",
    "AndroidX, Jetpack Compose, Room, WorkManager, DataStore, CameraX, Media3, Glance" to "Apache License 2.0",
    "Dagger / Hilt" to "Apache License 2.0",
    "Retrofit, OkHttp, Moshi" to "Apache License 2.0",
    "Coil" to "Apache License 2.0",
    "ZXing" to "Apache License 2.0",
    "Kotlin and kotlinx.coroutines" to "Apache License 2.0",
    "WebRTC (stream-webrtc-android)" to "BSD 3-Clause",
    "Firebase Cloud Messaging" to "Google APIs Terms of Service",
)

/** Version and build info (tap to copy), open-source licences, and shortcuts to terms and the changelog. */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLegal: () -> Unit, onOpenChangelog: () -> Unit) {
    val context = LocalContext.current
    val info = "MENCH ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
    val copied = stringResource(R.string.sx_about_copied)

    SettingsScaffold(title = stringResource(R.string.sx_about_title), onBack = onBack) {
        SettingsSection(title = stringResource(R.string.sx_about_version)) {
            SettingsRow(
                title = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                subtitle = stringResource(R.string.sx_about_tap_copy),
                onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("MENCH version", info))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                },
            )
        }
        SettingsSection(title = null) {
            SettingsRow(title = stringResource(R.string.legal_title), onClick = onOpenLegal)
            SettingsRow(title = stringResource(R.string.changelog_title), onClick = onOpenChangelog)
        }
        SettingsSection(title = stringResource(R.string.sx_about_licenses)) {
            LICENSES.forEach { (name, license) -> SettingsRow(title = name, subtitle = license) }
        }
    }
}
