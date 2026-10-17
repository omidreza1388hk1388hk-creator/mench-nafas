package com.omidgame.mench.feature.chat.presentation

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.core.ui.theme.rememberReduceMotion
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Phase 3A — full-screen message effects (iMessage-style).
 *
 * An effect is chosen two ways: automatically, when a message contains one of the
 * trigger phrases in [MessageEffectTriggers] (detected on each phone from the message
 * text itself, so nothing extra travels over the wire), or by hand with a long-press
 * on the send button (sent as the ephemeral `screen_effect` realtime event).
 * [wire] must match SCREEN_EFFECTS in backend send-screen-effect.dto.ts.
 */
enum class MessageEffect(val wire: String, val emoji: String, val labelRes: Int, val durationMs: Int, val particles: Int) {
    HEARTS("hearts", "💗", R.string.effect_hearts, 4200, 34),
    CONFETTI("confetti", "🎉", R.string.effect_confetti, 3800, 70),
    NIGHT("night", "🌙", R.string.effect_night, 5200, 30),
    LANTERNS("lanterns", "🏮", R.string.effect_lanterns, 5600, 12),
    BALLOONS("balloons", "🎈", R.string.effect_balloons, 5000, 14),
    ;

    companion object {
        fun fromWire(wire: String?): MessageEffect? = MessageEffect.entries.firstOrNull { it.wire == wire }
    }
}

/** Finds the effect (if any) a piece of text should trigger. Pure and allocation-light, safe to call per message. */
object MessageEffectTriggers {
    // Order matters: the first effect with a matching phrase wins, so the birthday wish
    // comes before the generic "congratulations" (تولدت مبارک also contains مبارک).
    // Phrases are written already normalised (see normalize): lower-case, Persian ی/ک,
    // ZWNJ and punctuation turned into single spaces.
    private val phrases: List<Pair<MessageEffect, List<String>>> = listOf(
        MessageEffect.BALLOONS to listOf("happy birthday", "تولدت مبارک", "تولدت مبارکه", "تولد مبارک"),
        MessageEffect.HEARTS to listOf("i love you", "love you", "love u", "دوستت دارم", "عاشقتم"),
        MessageEffect.LANTERNS to listOf("miss you", "دلم برات تنگ", "دلتنگتم"),
        MessageEffect.NIGHT to listOf("good night", "goodnight", "شب بخیر", "شبت بخیر", "شبتون بخیر", "شب خوش"),
        MessageEffect.CONFETTI to listOf("congrats", "congratulations", "تبریک", "مبارک", "مبارکه"),
    )

    /** Long texts are never effect messages — also bounds the work done per message. */
    private const val MAX_LENGTH = 400

    /** [custom] are the user's own (effect, phrase) pairs from Settings; they win over the built-in phrases. */
    fun detect(text: String?, custom: List<Pair<MessageEffect, String>> = emptyList()): MessageEffect? {
        if (text.isNullOrBlank() || text.length > MAX_LENGTH) return null
        val normalized = normalize(text)
        for ((effect, phrase) in custom) {
            if (normalized.contains(" $phrase ")) return effect
        }
        for ((effect, list) in phrases) {
            for (phrase in list) {
                if (normalized.contains(" $phrase ")) return effect
            }
        }
        return null
    }

    /** Lower-cases, unifies Arabic/Persian letter forms, drops diacritics and turns every non-letter into one space, padded with a space on both ends. */
    internal fun normalize(text: String): String {
        val sb = StringBuilder(text.length + 2)
        sb.append(' ')
        var lastWasSpace = true
        for (raw in text) {
            if (raw in '\u064B'..'\u065F' || raw == '\u0640') continue // tashkeel and tatweel
            val c = when (raw) {
                '\u064A' -> '\u06CC' // Arabic yeh -> Persian yeh
                '\u0643' -> '\u06A9' // Arabic kaf -> Persian kaf
                else -> raw
            }
            if (c.isLetterOrDigit()) {
                sb.append(c.lowercaseChar())
                lastWasSpace = false
            } else if (!lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
            }
        }
        if (!lastWasSpace) sb.append(' ')
        return sb.toString()
    }
}

/** Switches for the romantic layer. Stored on the phone only (SharedPreferences), like the chat wallpaper. */
object MessageEffectSettings {
    const val PREFS = "message_effects"
    const val KEY_EFFECTS = "effects_enabled"
    const val KEY_HEARTBEAT_VIBRATION = "heartbeat_vibration"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Boolean = prefs(context).getBoolean(key, true)

    fun set(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    private const val KEY_SEASONAL_DAY = "seasonal_day"

    /** True the first time it is asked on a given calendar day (and records that), so the seasonal effect plays once a day. */
    fun claimSeasonalPlay(context: Context, todayIso: String): Boolean {
        if (prefs(context).getString(KEY_SEASONAL_DAY, null) == todayIso) return false
        prefs(context).edit().putString(KEY_SEASONAL_DAY, todayIso).apply()
        return true
    }

    private const val KEY_CUSTOM = "custom_triggers"
    const val MAX_CUSTOM_TRIGGERS = 12

    /** The user's own trigger phrases as (effect, normalised phrase). Stored as lines "wire<TAB>phrase". */
    fun customTriggers(context: Context): List<Pair<MessageEffect, String>> =
        (prefs(context).getString(KEY_CUSTOM, "") ?: "").lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            val effect = MessageEffect.fromWire(parts.getOrNull(0)) ?: return@mapNotNull null
            val phrase = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            effect to phrase
        }.toList()

    /** Adds a phrase for [effect]; returns false if it is empty, too long, a duplicate, or the list is full. */
    fun addCustomTrigger(context: Context, effect: MessageEffect, rawPhrase: String): Boolean {
        val phrase = MessageEffectTriggers.normalize(rawPhrase).trim()
        if (phrase.length !in 2..40) return false
        val current = customTriggers(context)
        if (current.size >= MAX_CUSTOM_TRIGGERS || current.any { it.second == phrase }) return false
        saveCustom(context, current + (effect to phrase))
        return true
    }

    fun removeCustomTrigger(context: Context, phrase: String) {
        saveCustom(context, customTriggers(context).filterNot { it.second == phrase })
    }

    private fun saveCustom(context: Context, list: List<Pair<MessageEffect, String>>) {
        prefs(context).edit().putString(KEY_CUSTOM, list.joinToString("\n") { it.first.wire + "\t" + it.second }).apply()
    }
}

/** A boolean from [MessageEffectSettings] as Compose state; updates live when changed from Settings. */
@Composable
fun rememberEffectSetting(key: String): Boolean {
    val context = LocalContext.current
    var value by remember(key) { mutableStateOf(MessageEffectSettings.get(context, key)) }
    DisposableEffect(context, key) {
        val prefs = context.applicationContext.getSharedPreferences(MessageEffectSettings.PREFS, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = MessageEffectSettings.get(context, key) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

/** Particle budget multiplier: halved on low-RAM devices (the performance guardrail from the master prompt). */
@Composable
private fun rememberParticleBudget(): Float {
    val context = LocalContext.current
    val quality = rememberGraphicsQuality()
    return remember(quality) { quality.particleFactor(GraphicsQuality.isLowRam(context)) }
}

/** A heart centred on the origin, about 1.9 units wide; scale it with withTransform. Shared with the heartbeat overlay. */
internal fun unitHeartPath(): Path = Path().apply {
    moveTo(0f, 0.9f)
    cubicTo(-1.6f, -0.1f, -0.7f, -1.1f, 0f, -0.35f)
    cubicTo(0.7f, -1.1f, 1.6f, -0.1f, 0f, 0.9f)
    close()
}

/** A crescent moon of radius 1 centred on the origin. */
private fun unitMoonPath(): Path {
    val disc = Path().apply { addOval(Rect(center = Offset.Zero, radius = 1f)) }
    val bite = Path().apply { addOval(Rect(center = Offset(0.48f, -0.18f), radius = 0.86f)) }
    return Path.combine(PathOperation.Difference, disc, bite)
}

/** One precomputed particle; every field is drawn from a seeded Random so a replay with the same key looks identical. */
private class Particle(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val size: Float,
    val phase: Float,
    val sway: Float,
    val color: Int,
)

private fun buildParticles(count: Int, seed: Int): List<Particle> {
    val rnd = java.util.Random(seed.toLong() * 7919L + 13L)
    return List(count) {
        Particle(
            x = rnd.nextFloat(),
            delay = rnd.nextFloat() * 0.45f,
            speed = 0.75f + rnd.nextFloat() * 0.5f,
            size = 0.7f + rnd.nextFloat() * 0.6f,
            phase = rnd.nextFloat() * 6.28f,
            sway = 0.01f + rnd.nextFloat() * 0.03f,
            color = rnd.nextInt(1000),
        )
    }
}

/** 0 -> 1 -> 0 envelope: quick fade-in, long plateau, gentle fade-out. */
private fun fade(t: Float): Float = min(min(t / 0.12f, (1f - t) / 0.2f), 1f).coerceIn(0f, 1f)

/** Progress of one particle (0..1) given overall progress and its start delay; outside 0..1 means "not on screen". */
private fun localT(p: Float, delay: Float): Float = (p - delay) / (1f - delay)

/**
 * Draws [effect] over its parent, once per [playKey] (bump the key to replay). It never
 * intercepts touches, so the chat underneath stays usable. Draws nothing under
 * "remove animations"; uses half the particles on low-RAM devices.
 */
@Composable
fun MessageEffectOverlay(effect: MessageEffect?, playKey: Int, modifier: Modifier = Modifier) {
    if (effect == null || playKey <= 0) return
    if (rememberReduceMotion()) return
    val budget = rememberParticleBudget()
    val progress = remember(playKey) { Animatable(0f) }
    LaunchedEffect(playKey) { progress.animateTo(1f, tween(effect.durationMs, easing = LinearEasing)) }

    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme.primary, scheme.secondary, scheme.tertiary) {
        listOf(
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            lerp(scheme.primary, Color.White, 0.5f),
            lerp(scheme.tertiary, Color.White, 0.5f),
        )
    }
    val scrim = scheme.scrim
    val particles = remember(playKey, effect, budget) { buildParticles(maxOf(4, (effect.particles * budget).toInt()), playKey) }
    val heart = remember { unitHeartPath() }
    val moon = remember { unitMoonPath() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        when (effect) {
            MessageEffect.HEARTS -> drawHearts(p, particles, palette, heart)
            MessageEffect.CONFETTI -> drawConfetti(p, particles, palette)
            MessageEffect.NIGHT -> drawNight(p, particles, palette, scrim, moon)
            MessageEffect.LANTERNS -> drawLanterns(p, particles, palette)
            MessageEffect.BALLOONS -> drawBalloons(p, particles, palette)
        }
    }
}

private fun DrawScope.drawHearts(p: Float, particles: List<Particle>, palette: List<Color>, heart: Path) {
    for (pt in particles) {
        val t = localT(p, pt.delay)
        if (t <= 0f || t >= 1f) continue
        val s = 11f * pt.size * density
        val x = pt.x * size.width + sin(pt.phase + t * 6f) * pt.sway * size.width
        val y = -s + t * pt.speed * (size.height + s * 2f)
        val color = palette[pt.color % palette.size].copy(alpha = 0.9f * fade(t))
        withTransform({
            translate(x, y)
            rotate(sin(pt.phase + t * 4f) * 22f, Offset.Zero)
            scale(s, s, Offset.Zero)
        }) { drawPath(heart, color) }
    }
}

private fun DrawScope.drawConfetti(p: Float, particles: List<Particle>, palette: List<Color>) {
    for (pt in particles) {
        val t = localT(p, pt.delay * 0.6f)
        if (t <= 0f || t >= 1f) continue
        val w = 9f * pt.size * density
        val h = w * 0.55f
        val x = pt.x * size.width + sin(pt.phase + t * 9f) * pt.sway * 3f * size.width
        val y = -h + t * pt.speed * (size.height + h * 2f)
        val color = palette[pt.color % palette.size].copy(alpha = fade(t))
        withTransform({
            translate(x, y)
            rotate((pt.phase + t * 14f) * 57.3f, Offset.Zero)
        }) { drawRect(color, topLeft = Offset(-w / 2f, -h / 2f), size = Size(w, h)) }
    }
}

private fun DrawScope.drawNight(p: Float, particles: List<Particle>, palette: List<Color>, scrim: Color, moon: Path) {
    val env = fade(p)
    drawRect(scrim.copy(alpha = 0.42f * env))
    val starColor = lerp(palette[2], Color.White, 0.65f)
    for (pt in particles) {
        val twinkle = 0.5f + 0.5f * sin(pt.phase + p * 14f)
        val r = (1.2f + 1.6f * pt.size * twinkle) * density
        drawCircle(starColor.copy(alpha = env * (0.35f + 0.65f * twinkle)), radius = r, center = Offset(pt.x * size.width, (0.05f + pt.delay * 1.5f) * size.height))
    }
    // The moon drifts in an arc from the top end of the screen towards the start side.
    val mx = size.width * (0.82f - 0.5f * p)
    val my = size.height * (0.16f - 0.05f * sin(p * 3.14f))
    val r = 34f * density
    drawCircle(palette[2].copy(alpha = 0.18f * env), radius = r * 2.2f, center = Offset(mx, my))
    withTransform({
        translate(mx, my)
        scale(r, r, Offset.Zero)
    }) { drawPath(moon, lerp(palette[2], Color.White, 0.55f).copy(alpha = env)) }
}

private fun DrawScope.drawLanterns(p: Float, particles: List<Particle>, palette: List<Color>) {
    for (pt in particles) {
        val t = localT(p, pt.delay)
        if (t <= 0f || t >= 1f) continue
        val w = 22f * pt.size * density
        val h = w * 1.25f
        val x = pt.x * size.width + sin(pt.phase + t * 3f) * pt.sway * 4f * size.width
        val y = size.height + h - t * pt.speed * (size.height + h * 2f)
        val flicker = 0.85f + 0.15f * sin(pt.phase + t * 40f)
        val a = fade(t) * flicker
        val base = lerp(palette[2], palette[0], (pt.color % 3) / 4f)
        drawCircle(base.copy(alpha = 0.10f * a), radius = w * 2.6f, center = Offset(x, y))
        drawCircle(base.copy(alpha = 0.18f * a), radius = w * 1.5f, center = Offset(x, y))
        drawRoundRect(base.copy(alpha = a), topLeft = Offset(x - w / 2f, y - h / 2f), size = Size(w, h), cornerRadius = CornerRadius(w * 0.35f))
        drawRoundRect(lerp(base, Color.Black, 0.4f).copy(alpha = a), topLeft = Offset(x - w * 0.3f, y - h / 2f - 3f * density), size = Size(w * 0.6f, 4f * density), cornerRadius = CornerRadius(2f * density))
    }
}

private fun DrawScope.drawBalloons(p: Float, particles: List<Particle>, palette: List<Color>) {
    for (pt in particles) {
        val t = localT(p, pt.delay)
        if (t <= 0f || t >= 1f) continue
        val w = 30f * pt.size * density
        val h = w * 1.25f
        val sway = sin(pt.phase + t * 5f) * pt.sway * 3f * size.width
        val x = pt.x * size.width + sway
        val y = size.height + h * 2f - t * pt.speed * (size.height + h * 4f)
        val a = fade(t)
        val color = palette[pt.color % palette.size]
        // String first so the balloon covers its top end.
        drawLine(color.copy(alpha = 0.55f * a), Offset(x, y + h / 2f), Offset(x - sway * 0.6f, y + h / 2f + h * 0.9f), strokeWidth = 1.2f * density)
        drawOval(color.copy(alpha = 0.95f * a), topLeft = Offset(x - w / 2f, y - h / 2f), size = Size(w, h))
        drawOval(Color.White.copy(alpha = 0.35f * a), topLeft = Offset(x - w * 0.28f, y - h * 0.34f), size = Size(w * 0.2f, h * 0.28f))
    }
}

/** Picks an effect by hand (long-press on send). The caller sends the message and the effect when [onPick] fires. */
@Composable
fun EffectPickerDialog(onDismiss: () -> Unit, onPick: (MessageEffect) -> Unit) {
    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        title = { Text(stringResource(R.string.effect_picker_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MessageEffect.entries.forEach { effect ->
                    TextButton(onClick = { onPick(effect) }, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(effect.emoji, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = stringResource(effect.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

/** Settings → «افکت‌ها و قلب‌تپش»: the two switches of the romantic layer. */
@Composable
fun MessageEffectsSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var effects by remember { mutableStateOf(MessageEffectSettings.get(context, MessageEffectSettings.KEY_EFFECTS)) }
    var vibration by remember { mutableStateOf(MessageEffectSettings.get(context, MessageEffectSettings.KEY_HEARTBEAT_VIBRATION)) }
    var touchHaptics by remember { mutableStateOf(com.omidgame.mench.core.ui.theme.HapticSettings.get(context, com.omidgame.mench.core.ui.theme.HapticSettings.KEY_TOUCH)) }
    var affection by remember { mutableStateOf(com.omidgame.mench.core.ui.theme.HapticSettings.get(context, com.omidgame.mench.core.ui.theme.HapticSettings.KEY_AFFECTION)) }
    var custom by remember { mutableStateOf(MessageEffectSettings.customTriggers(context)) }
    var newPhrase by remember { mutableStateOf("") }
    var newEffect by remember { mutableStateOf(MessageEffect.HEARTS) }
    var quality by remember { mutableStateOf(GraphicsQuality.get(context)) }
    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        title = { Text(stringResource(R.string.effects_settings_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingSwitchRow(stringResource(R.string.effects_setting_words), stringResource(R.string.effects_setting_words_hint), effects) {
                    effects = it
                    MessageEffectSettings.set(context, MessageEffectSettings.KEY_EFFECTS, it)
                }
                SettingSwitchRow(stringResource(R.string.effects_setting_vibration), stringResource(R.string.effects_setting_vibration_hint), vibration) {
                    vibration = it
                    MessageEffectSettings.set(context, MessageEffectSettings.KEY_HEARTBEAT_VIBRATION, it)
                }
                SettingSwitchRow(stringResource(R.string.haptics_touch), stringResource(R.string.haptics_touch_hint), touchHaptics) {
                    touchHaptics = it
                    com.omidgame.mench.core.ui.theme.HapticSettings.set(context, com.omidgame.mench.core.ui.theme.HapticSettings.KEY_TOUCH, it)
                }
                SettingSwitchRow(stringResource(R.string.haptics_affection), stringResource(R.string.haptics_affection_hint), affection) {
                    affection = it
                    com.omidgame.mench.core.ui.theme.HapticSettings.set(context, com.omidgame.mench.core.ui.theme.HapticSettings.KEY_AFFECTION, it)
                }
                Text(stringResource(R.string.quality_title), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GraphicsQuality.entries.forEach { q ->
                        androidx.compose.material3.FilterChip(
                            selected = quality == q,
                            onClick = { quality = q; GraphicsQuality.set(context, q) },
                            label = { Text(stringResource(when (q) {
                                GraphicsQuality.AUTO -> R.string.quality_auto
                                GraphicsQuality.LOW -> R.string.quality_low
                                GraphicsQuality.HIGH -> R.string.quality_high
                            })) },
                        )
                    }
                }
                Text(stringResource(R.string.quality_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.effects_custom_title), style = MaterialTheme.typography.titleSmall)
                custom.forEach { (effect, phrase) ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(effect.emoji + "  " + phrase, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            MessageEffectSettings.removeCustomTrigger(context, phrase)
                            custom = MessageEffectSettings.customTriggers(context)
                        }) { Text(stringResource(R.string.counters_delete)) }
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = newPhrase,
                    onValueChange = { if (it.length <= 40) newPhrase = it },
                    label = { Text(stringResource(R.string.effects_custom_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MessageEffect.entries.forEach { e ->
                        androidx.compose.material3.FilterChip(selected = newEffect == e, onClick = { newEffect = e }, label = { Text(e.emoji) })
                    }
                }
                TextButton(
                    enabled = newPhrase.isNotBlank(),
                    onClick = {
                        if (MessageEffectSettings.addCustomTrigger(context, newEffect, newPhrase)) {
                            custom = MessageEffectSettings.customTriggers(context)
                            newPhrase = ""
                        }
                    },
                ) { Text(stringResource(R.string.effects_custom_add)) }
            }
        },
    )
}

@Composable
private fun SettingSwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Absolute difference helper kept private to this file; see [HeartbeatComponents] for the beat curve. */
internal fun bump(x: Float, center: Float, width: Float): Float = (1f - abs(x - center) / width).coerceAtLeast(0f)
