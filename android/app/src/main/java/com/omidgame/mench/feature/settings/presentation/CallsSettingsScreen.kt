package com.omidgame.mench.feature.settings.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.settings.ChoiceOption
import com.omidgame.mench.core.ui.settings.SettingsRow
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.core.ui.settings.SingleChoiceRow
import com.omidgame.mench.core.ui.settings.SwitchRow
import com.omidgame.mench.core.webrtc.CallDiagnostics
import com.omidgame.mench.core.webrtc.CallSettingsStore
import com.omidgame.mench.core.webrtc.rememberCallSettings
import com.omidgame.mench.feature.settings.domain.CallDiagnosticsText
import com.omidgame.mench.feature.settings.domain.CallQualityPreset

/** Calls: quality preset, audio processing, speaker and camera defaults, and the last call's diagnostics. */
@Composable
fun CallsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = rememberCallSettings()
    val diagnostics = CallDiagnostics.last
    val copied = stringResource(R.string.sx_calls_diag_copied)

    SettingsScaffold(title = stringResource(R.string.sx_calls_title), onBack = onBack) {
        SettingsSection(title = stringResource(R.string.sx_calls_quality_section)) {
            SingleChoiceRow(
                title = stringResource(R.string.sx_calls_quality),
                subtitle = stringResource(R.string.sx_calls_quality_sub),
                options = listOf(
                    ChoiceOption(CallQualityPreset.AUTO, stringResource(R.string.sx_calls_quality_auto)),
                    ChoiceOption(CallQualityPreset.DATA_SAVER, stringResource(R.string.sx_calls_quality_saver)),
                    ChoiceOption(CallQualityPreset.HIGH, stringResource(R.string.sx_calls_quality_high)),
                ),
                selected = s.qualityPreset,
                onSelected = { CallSettingsStore.setQuality(context, it) },
            )
        }
        SettingsSection(title = stringResource(R.string.sx_calls_audio)) {
            SwitchRow(
                title = stringResource(R.string.sx_calls_noise),
                subtitle = stringResource(R.string.sx_calls_audio_sub),
                checked = s.noiseSuppression,
                onCheckedChange = { CallSettingsStore.setNoiseSuppression(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_calls_echo),
                checked = s.echoCancellation,
                onCheckedChange = { CallSettingsStore.setEchoCancellation(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_calls_speaker),
                subtitle = stringResource(R.string.sx_calls_speaker_sub),
                checked = s.voiceStartsOnSpeaker,
                onCheckedChange = { CallSettingsStore.setVoiceSpeaker(context, it) },
            )
        }
        SettingsSection(title = stringResource(R.string.sx_calls_video)) {
            SingleChoiceRow(
                title = stringResource(R.string.sx_calls_camera),
                options = listOf(
                    ChoiceOption(true, stringResource(R.string.sx_calls_camera_front)),
                    ChoiceOption(false, stringResource(R.string.sx_calls_camera_back)),
                ),
                selected = s.frontCameraDefault,
                onSelected = { CallSettingsStore.setFrontCamera(context, it) },
            )
            SwitchRow(
                title = stringResource(R.string.sx_calls_mirror),
                subtitle = stringResource(R.string.sx_calls_mirror_sub),
                checked = s.mirrorFrontCamera,
                onCheckedChange = { CallSettingsStore.setMirrorFront(context, it) },
            )
        }
        SettingsSection(title = stringResource(R.string.sx_calls_diag)) {
            if (diagnostics == null) {
                Text(
                    stringResource(R.string.sx_calls_diag_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            } else {
                SettingsRow(
                    title = stringResource(R.string.sx_calls_diag_last),
                    subtitle = CallDiagnosticsText.format(diagnostics),
                )
                SettingsRow(
                    title = stringResource(R.string.sx_calls_diag_copy),
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("MENCH call diagnostics", CallDiagnosticsText.format(diagnostics)))
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}
