package com.omidgame.mench.feature.chat.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omidgame.mench.core.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch

/** Phase 3B — small reusable graphics modifiers. */

/**
 * Depth: the surface leans towards your finger (up to [maxDegrees]) and springs back when you let go.
 * Does not consume the gesture beyond the drag, and is a no-op under "remove animations".
 */
fun Modifier.tiltOnTouch(maxDegrees: Float = 9f): Modifier = composed {
    val reduce = rememberReduceMotion()
    if (reduce) return@composed this
    val scope = rememberCoroutineScope()
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    this
        .pointerInput(maxDegrees) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun lean(x: Float, y: Float) {
                    val nx = (x / size.width.coerceAtLeast(1) - 0.5f) * 2f
                    val ny = (y / size.height.coerceAtLeast(1) - 0.5f) * 2f
                    scope.launch { rotY.snapTo((nx * maxDegrees).coerceIn(-maxDegrees, maxDegrees)) }
                    scope.launch { rotX.snapTo((-ny * maxDegrees).coerceIn(-maxDegrees, maxDegrees)) }
                }
                lean(down.position.x, down.position.y)
                drag(down.id) { change -> lean(change.position.x, change.position.y) }
                scope.launch { rotX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                scope.launch { rotY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            }
        }
        .graphicsLayer {
            rotationX = rotX.value
            rotationY = rotY.value
            cameraDistance = 14f * density
        }
}

/**
 * A frosted-glass look: a faint light gradient and a thin bright edge over whatever is behind it.
 * (Real backdrop blur is not available to a plain Compose surface, so this fakes the feel with layered translucency.)
 */
fun Modifier.glass(corner: Dp = 16.dp, tint: Color = Color.White): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.07f))), shape)
        .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.10f))), shape)
}

/**
 * Micro-interaction: the element shrinks slightly while a finger is down and springs back on release.
 * Observes touches without consuming them, so it can sit next to clickable/combinedClickable. No-op under "remove animations".
 */
fun Modifier.pressScale(pressedScale: Float = 0.96f): Modifier = composed {
    val reduce = rememberReduceMotion()
    if (reduce) return@composed this
    var pressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "pressScale",
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                // Wait for every finger to lift (or the gesture to be taken over), without consuming anything.
                do {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * Depth from the phone itself: the surface leans a few degrees as you tilt the device (accelerometer, low-pass filtered).
 * Does nothing without the sensor, under "remove animations", or in battery-saver graphics quality.
 */
fun Modifier.gyroTilt(maxDegrees: Float = 5f): Modifier = composed {
    val reduce = rememberReduceMotion()
    val quality = rememberGraphicsQuality()
    val context = androidx.compose.ui.platform.LocalContext.current
    var rotX by remember { mutableStateOf(0f) }
    var rotY by remember { mutableStateOf(0f) }
    androidx.compose.runtime.DisposableEffect(reduce, quality) {
        val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = manager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        if (reduce || quality == GraphicsQuality.LOW || manager == null || sensor == null) {
            return@DisposableEffect onDispose {}
        }
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val tiltX = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val tiltY = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                // Low-pass so hand tremor does not make the card shiver.
                rotY += 0.12f * (-tiltX * maxDegrees - rotY)
                rotX += 0.12f * (tiltY * maxDegrees - rotX)
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    this.graphicsLayer {
        rotationX = rotX
        rotationY = rotY
        cameraDistance = 14f * density
    }
}

/** True when a message created at [createdAtMillis] is still "fresh" at [nowMillis] — it just arrived or was just sent. */
internal fun isFreshMessage(createdAtMillis: Long, nowMillis: Long, windowMs: Long = 2_500L): Boolean = nowMillis - createdAtMillis in 0..windowMs

/**
 * Pop-in for a brand-new message: it springs from 82 % to full size while fading up. Messages loaded from history
 * (older than a couple of seconds) and "remove animations" skip it.
 */
fun Modifier.popInIfNew(createdAtMillis: Long): Modifier = composed {
    val reduce = rememberReduceMotion()
    val fresh = remember { isFreshMessage(createdAtMillis, System.currentTimeMillis()) }
    if (reduce || !fresh) return@composed this
    val scale = remember { Animatable(0.82f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = ((scale.value - 0.82f) / 0.18f).coerceIn(0.4f, 1f)
    }
}
