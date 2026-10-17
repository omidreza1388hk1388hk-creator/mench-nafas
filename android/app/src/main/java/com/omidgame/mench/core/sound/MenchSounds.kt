package com.omidgame.mench.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.feature.settings.domain.SoundSettingsRules

/** Short UI sounds (Kenney "Interface Sounds", CC0). Follows the system sound volume; one switch in Appearance settings. */
enum class MenchSound(val rawRes: Int) {
    SENT(R.raw.snd_sent),
    RECEIVED(R.raw.snd_received),
    ERROR(R.raw.snd_error),
    CONFIRM(R.raw.snd_confirm),
}

object MenchSounds {
    private const val PREFS = "mench_ui_sounds"
    private const val KEY_ENABLED = "enabled"

    @Volatile private var pool: SoundPool? = null
    private val ids = HashMap<MenchSound, Int>()
    private val loaded = HashSet<Int>()

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    @Synchronized
    private fun ensure(context: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        p.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) synchronized(loaded) { loaded += sampleId } }
        MenchSound.values().forEach { ids[it] = p.load(context.applicationContext, it.rawRes, 1) }
        pool = p
        return p
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Settings Phase 2: volume and one switch per event (defaults = the old fixed 0.7 volume, every event on).
    fun volume(context: Context): Float = SoundSettingsRules.clampVolume(prefs(context).getFloat("volume", SoundSettingsRules.DEFAULT_VOLUME))
    fun setVolume(context: Context, v: Float) { prefs(context).edit().putFloat("volume", SoundSettingsRules.clampVolume(v)).apply() }
    fun isEventEnabled(context: Context, sound: MenchSound): Boolean = prefs(context).getBoolean(SoundSettingsRules.eventKey(sound.name), true)
    fun setEventEnabled(context: Context, sound: MenchSound, on: Boolean) {
        prefs(context).edit().putBoolean(SoundSettingsRules.eventKey(sound.name), on).apply()
    }

    /** Best effort: never throws, silently does nothing until the sample finished loading. */
    fun play(context: Context, sound: MenchSound) {
        runCatching {
            if (!SoundSettingsRules.shouldPlay(isEnabled(context), isEventEnabled(context, sound))) return
            val p = ensure(context)
            val id = ids[sound] ?: return
            val level = volume(context)
            if (synchronized(loaded) { id in loaded }) p.play(id, level, level, 1, 0, 1f)
        }
    }
}

/** The settings row: "Sounds" with a switch. Self-contained (reads/writes MenchSounds directly). */
@Composable
fun SoundSettingRow() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(MenchSounds.isEnabled(context)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.settings_sounds))
        Switch(checked = enabled, onCheckedChange = { enabled = it; MenchSounds.setEnabled(context, it); if (it) MenchSounds.play(context, MenchSound.CONFIRM) })
    }
}
