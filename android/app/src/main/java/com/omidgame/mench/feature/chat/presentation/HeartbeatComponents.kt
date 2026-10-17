package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/** Phase 3A — the heartbeat: hold a heart, the partner's phone beats in the same rhythm. */

/** One full "lub-dub" cycle, in ms. Sender button, receiver vibration and the overlay all use it so they stay in step. */
private const val BEAT_CYCLE_MS = 780

/** The server rejects holds shorter than this or longer than [MAX_HOLD_MS]. */
private const val MIN_HOLD_MS = 1000L
private const val MAX_HOLD_MS = 10_000L

private fun vibratorOf(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

/**
 * Vibrates "lub-dub" beats for about [durationMs] (strong first beat, softer second).
 * Does nothing if the device has no vibrator or lacks amplitude control support; capped at 10 s.
 */
fun vibrateHeartbeatFor(context: Context, durationMs: Long) {
    runCatching {
        val vibrator = vibratorOf(context) ?: return
        val cycles = (durationMs.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS) / BEAT_CYCLE_MS).toInt().coerceAtLeast(1)
        val timings = LongArray(cycles * 4)
        val amplitudes = IntArray(cycles * 4)
        for (i in 0 until cycles) {
            val b = i * 4
            timings[b] = 55L; amplitudes[b] = 210          // lub
            timings[b + 1] = 95L; amplitudes[b + 1] = 0    // gap
            timings[b + 2] = 55L; amplitudes[b + 2] = 140  // dub
            timings[b + 3] = (BEAT_CYCLE_MS - 205).toLong(); amplitudes[b + 3] = 0 // rest
        }
        if (vibrator.hasAmplitudeControl()) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
    }
}

/** Stops any vibration this app started (used when the sender lets go of the heart). */
fun cancelHeartbeatVibration(context: Context) {
    runCatching { vibratorOf(context)?.cancel() }
}

/** 0..1 strength of the heart at [cycle] (0..1 through one beat): a big first thump and a smaller second one. */
internal fun beatPulse(cycle: Float): Float = maxOf(bump(cycle, 0.07f, 0.07f), 0.7f * bump(cycle, 0.27f, 0.07f))

/**
 * The hold-to-send heart. While held it pulses and vibrates the sender's own phone too, so
 * you feel what they will feel. On release the held duration (1–10 s) is sent; [onSend]'s
 * second argument reports whether the partner was online to feel it.
 */
@Composable
fun HeartbeatHoldButton(
    onSend: (durationMs: Long, onResult: (delivered: Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = rememberReduceMotion()
    var pressed by remember { mutableStateOf(false) }
    // 0 = hint, 1 = too short, 2 = delivered, 3 = partner offline
    var status by remember { mutableStateOf(0) }

    val pulse = rememberInfiniteTransition(label = "holdPulse")
    val cycle by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BEAT_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "holdCycle",
    )
    val restScale by animateFloatAsState(if (pressed) 1f else 0.94f, label = "holdRest")
    val scale = if (pressed && !reduceMotion) restScale * (1f + 0.16f * beatPulse(cycle)) else restScale

    LaunchedEffect(status) {
        if (status != 0) {
            delay(2500)
            status = 0
        }
    }

    val holdDescription = stringResource(R.string.heartbeat_hold_description)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(MenchBrand.heroBrush())
                .semantics { contentDescription = holdDescription }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            HeartbeatSync.onPress()
                            val start = SystemClock.elapsedRealtime()
                            vibrateHeartbeatFor(context, MAX_HOLD_MS)
                            tryAwaitRelease()
                            pressed = false
                            HeartbeatSync.onRelease(SystemClock.elapsedRealtime())
                            cancelHeartbeatVibration(context)
                            val held = SystemClock.elapsedRealtime() - start
                            if (held >= MIN_HOLD_MS) {
                                onSend(held.coerceAtMost(MAX_HOLD_MS)) { delivered -> status = if (delivered) 2 else 3 }
                            } else {
                                status = 1
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
        }
        if (status == 2) DrawnCheck(size = 22.dp)
        Text(
            text = stringResource(
                when (status) {
                    1 -> R.string.heartbeat_too_short
                    2 -> R.string.heartbeat_delivered
                    3 -> R.string.heartbeat_offline
                    else -> R.string.heartbeat_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Full-screen heartbeat shown on the receiving phone for [durationMs] (replays when [playKey]
 * changes). Tap anywhere to dismiss early. Under "remove animations" the heart stays still.
 */
@Composable
fun HeartbeatOverlay(playKey: Int, durationMs: Long, modifier: Modifier = Modifier) {
    if (playKey <= 0) return
    val reduceMotion = rememberReduceMotion()
    var visible by remember(playKey) { mutableStateOf(true) }
    LaunchedEffect(playKey) {
        visible = true
        delay(durationMs.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS) + 400L)
        visible = false
    }
    val scheme = MaterialTheme.colorScheme
    val heart = remember { unitHeartPath() }
    val heartDescription = stringResource(R.string.heartbeat_overlay_description)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(320)),
        modifier = modifier.fillMaxSize(),
    ) {
        val transition = rememberInfiniteTransition(label = "heartbeat")
        val cycle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BEAT_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "heartbeatCycle",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = heartDescription }
                .pointerInput(playKey) { detectTapGestures { visible = false } },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pulse = if (reduceMotion) 0f else beatPulse(cycle)
                val minDim = minOf(size.width, size.height)
                val center = Offset(size.width / 2f, size.height / 2f)
                drawRect(scheme.scrim.copy(alpha = 0.5f + 0.08f * pulse))
                drawCircle(scheme.primary.copy(alpha = 0.22f + 0.2f * pulse), radius = minDim * (0.55f + 0.1f * pulse), center = center)
                if (!reduceMotion) {
                    drawCircle(
                        scheme.primary.copy(alpha = (1f - cycle) * 0.45f),
                        radius = minDim * (0.22f + 0.38f * cycle),
                        center = center,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                val s = minDim * 0.2f * (1f + 0.22f * pulse)
                withTransform({
                    translate(center.x, center.y - s * 0.15f)
                    scale(s, s, Offset.Zero)
                }) { drawPath(heart, lerp(scheme.primary, Color.White, 0.25f)) }
            }
        }
    }
}
