package com.omidgame.mench.feature.settings.presentation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omidgame.mench.R
import com.omidgame.mench.core.sound.MenchSound
import com.omidgame.mench.core.sound.MenchSounds
import com.omidgame.mench.core.ui.settings.ChoiceOption
import com.omidgame.mench.core.ui.settings.InfoBanner
import com.omidgame.mench.core.ui.settings.SettingsScaffold
import com.omidgame.mench.core.ui.settings.SettingsSection
import com.omidgame.mench.core.ui.settings.SingleChoiceRow
import com.omidgame.mench.core.ui.settings.SliderRow
import com.omidgame.mench.core.ui.settings.SwitchRow
import com.omidgame.mench.feature.settings.domain.SoundSettingsRules
import com.omidgame.mench.feature.settings.domain.TextWeightChoice

/** Appearance extras: text weight, animation level and the sound options (volume + one switch per event). */
@Composable
fun AppearanceExtrasScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val weightPrefs = remember { context.applicationContext.getSharedPreferences("mench_text_weight", Context.MODE_PRIVATE) }
    val animPrefs = remember { context.applicationContext.getSharedPreferences("mench_animation", Context.MODE_PRIVATE) }
    var weight by remember { mutableStateOf(TextWeightChoice.fromStorage(weightPrefs.getString("weight", null))) }
    var reduced by remember { mutableStateOf(animPrefs.getBoolean("reduced", false)) }
    var soundsOn by remember { mutableStateOf(MenchSounds.isEnabled(context)) }
    var volume by remember { mutableStateOf(MenchSounds.volume(context)) }
    val events = remember { MenchSound.values().associateWith { mutableStateOf(MenchSounds.isEventEnabled(context, it)) } }

    SettingsScaffold(title = stringResource(R.string.sx_ap_title), onBack = onBack) {
        SettingsSection(title = stringResource(R.string.sx_ap_text)) {
            SingleChoiceRow(
                title = stringResource(R.string.sx_ap_weight),
                subtitle = stringResource(R.string.sx_ap_weight_sub),
                options = listOf(
                    ChoiceOption(TextWeightChoice.REGULAR, stringResource(R.string.sx_ap_weight_regular)),
                    ChoiceOption(TextWeightChoice.MEDIUM, stringResource(R.string.sx_ap_weight_medium)),
                ),
                selected = weight,
                onSelected = { weight = it; weightPrefs.edit().putString("weight", it.storageValue).apply() },
            )
        }
        SettingsSection(title = stringResource(R.string.sx_ap_motion)) {
            SingleChoiceRow(
                title = stringResource(R.string.sx_ap_animation),
                subtitle = stringResource(R.string.sx_ap_animation_sub),
                options = listOf(
                    ChoiceOption(false, stringResource(R.string.sx_ap_animation_full)),
                    ChoiceOption(true, stringResource(R.string.sx_ap_animation_reduced)),
                ),
                selected = reduced,
                onSelected = { reduced = it; animPrefs.edit().putBoolean("reduced", it).apply() },
            )
        }
        SettingsSection(title = stringResource(R.string.settings_sounds)) {
            SwitchRow(
                title = stringResource(R.string.settings_sounds),
                checked = soundsOn,
                onCheckedChange = {
                    soundsOn = it
                    MenchSounds.setEnabled(context, it)
                    if (it) MenchSounds.play(context, MenchSound.CONFIRM)
                },
            )
            SliderRow(
                title = stringResource(R.string.sx_ap_volume),
                subtitle = stringResource(R.string.sx_ap_volume_sub),
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = {
                    MenchSounds.setVolume(context, volume)
                    MenchSounds.play(context, MenchSound.CONFIRM) // preview at the new level
                },
                valueRange = SoundSettingsRules.MIN_VOLUME..SoundSettingsRules.MAX_VOLUME,
                valueLabel = "${(volume * 100).toInt()}%",
                enabled = soundsOn,
            )
            MenchSound.values().forEach { sound ->
                val state = events.getValue(sound)
                SwitchRow(
                    title = stringResource(soundTitle(sound)),
                    checked = state.value,
                    enabled = soundsOn,
                    onCheckedChange = {
                        state.value = it
                        MenchSounds.setEventEnabled(context, sound, it)
                        if (it) MenchSounds.play(context, sound)
                    },
                )
            }
        }
        if (soundsOn) InfoBanner(text = stringResource(R.string.sx_ap_sounds_note))
    }
}

private fun soundTitle(sound: MenchSound): Int = when (sound) {
    MenchSound.SENT -> R.string.sx_ap_sound_sent
    MenchSound.RECEIVED -> R.string.sx_ap_sound_received
    MenchSound.ERROR -> R.string.sx_ap_sound_error
    MenchSound.CONFIRM -> R.string.sx_ap_sound_confirm
}
