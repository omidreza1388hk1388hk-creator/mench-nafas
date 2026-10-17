package com.omidgame.mench.feature.chat.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.media.RecorderLive

/** A press shorter than this is a tap: it starts hands-free (locked) recording, exactly like the old tap-to-start flow. */
private const val TAP_LOCK_MS = 350L

/**
 * Telegram-style hold-to-record on the mic button.
 *
 * - press and hold: [onStart] begins recording (return false if it could not start, e.g. the
 *   microphone permission is being requested — the gesture then does nothing more);
 * - release: [onFinish] with `send = true`;
 * - slide toward the start edge: [onFinish] with `send = false` (cancel) — the direction flips in RTL;
 * - slide up: [onLocked] — recording continues hands-free and the caller shows Send / Cancel;
 * - quick tap (< [TAP_LOCK_MS]): also [onLocked], so a tap-tap flow keeps working;
 * - [onDrag] reports 0..1 progress toward cancel and toward lock for the live hints.
 */
@Composable
fun Modifier.holdToRecord(
    enabled: Boolean,
    onStart: () -> Boolean,
    onLocked: () -> Unit,
    onFinish: (send: Boolean) -> Unit,
    onDrag: (cancelFraction: Float, lockFraction: Float) -> Unit,
): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val startState by rememberUpdatedState(onStart)
    val lockedState by rememberUpdatedState(onLocked)
    val finishState by rememberUpdatedState(onFinish)
    val dragState by rememberUpdatedState(onDrag)

    return this.pointerInput(enabled, rtl) {
        if (!enabled) return@pointerInput
        val cancelDistance = 110.dp.toPx()
        val lockDistance = 80.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            if (!startState()) {
                waitForUpOrCancellation()
                return@awaitEachGesture
            }
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    dragState(0f, 0f)
                    finishState(false)
                    return@awaitEachGesture
                }
                if (change.changedToUp()) {
                    change.consume()
                    dragState(0f, 0f)
                    if (change.uptimeMillis - down.uptimeMillis < TAP_LOCK_MS) lockedState() else finishState(true)
                    return@awaitEachGesture
                }
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                val towardCancel = if (rtl) dx else -dx
                val cancelFraction = (towardCancel / cancelDistance).coerceIn(0f, 1f)
                val lockFraction = (-dy / lockDistance).coerceIn(0f, 1f)
                change.consume()
                dragState(cancelFraction, lockFraction)
                if (cancelFraction >= 1f) {
                    dragState(0f, 0f)
                    finishState(false)
                    waitForUpOrCancellation()
                    return@awaitEachGesture
                }
                if (lockFraction >= 1f) {
                    dragState(0f, 0f)
                    lockedState()
                    return@awaitEachGesture
                }
            }
        }
    }
}

/**
 * Replaces the text field while recording: pulsing red dot, timer, live waveform and either
 * the "slide to cancel" hint (holding) or a Cancel button (locked).
 */
@Composable
fun RecordingBar(
    live: RecorderLive,
    locked: Boolean,
    cancelFraction: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pulse = rememberInfiniteTransition(label = "recDot")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "recDotAlpha",
    )
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        )
        Text(
            text = formatVoiceTime(live.elapsedMs),
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        LiveWaveform(levels = live.levels, modifier = Modifier.weight(1f).height(28.dp))
        if (locked) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        } else {
            Text(
                text = stringResource(R.string.voice_slide_to_cancel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .graphicsLayer {
                        // Follows the finger toward the cancel edge and fades out as you get there.
                        translationX = cancelFraction * 48.dp.toPx() * (if (rtl) 1f else -1f)
                        alpha = 1f - cancelFraction
                    },
            )
        }
    }
}

/** Latest levels (0..1), newest on the end side; bars before the first sample stay as a faint baseline. */
@Composable
private fun LiveWaveform(levels: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val slots = 40
        val step = size.width / slots
        val stroke = step * 0.5f
        val minHeight = 3.dp.toPx()
        val offset = slots - levels.size
        for (slot in 0 until slots) {
            val level = if (slot >= offset) levels[slot - offset] else 0f
            val h = (size.height * level).coerceIn(minHeight, size.height)
            val x = step * slot + step / 2f
            drawLine(
                color = if (slot >= offset) color else color.copy(alpha = 0.25f),
                start = Offset(x, (size.height - h) / 2f),
                end = Offset(x, (size.height + h) / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Small capsule that rises above the mic while holding; fills as you slide up toward the lock. */
@Composable
fun LockHint(lockFraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 52.dp)
            .graphicsLayer {
                translationY = -lockFraction * 28.dp.toPx()
                alpha = 0.55f + 0.45f * lockFraction
            }
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.TopCenter,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp).size(18.dp),
        )
    }
}
