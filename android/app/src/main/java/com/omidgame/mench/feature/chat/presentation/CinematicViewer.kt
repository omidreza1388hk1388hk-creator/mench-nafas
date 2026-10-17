package com.omidgame.mench.feature.chat.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.omidgame.mench.core.ui.theme.rememberReduceMotion
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Phase 3B — the cinematic image viewer.
 * Fades and scales in; double-tap zooms with a spring; pinch zooms up to 6×; one finger pans when zoomed;
 * when NOT zoomed, dragging up or down moves the picture and fades the backdrop, and letting go past a threshold
 * closes it (like a photo app). A single tap closes it too.
 */

private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val DISMISS_DRAG_PX = 220f
private const val FULL_FADE_DRAG_PX = 600f

/** 0 (not dragged) .. 1 (fully faded) for a vertical drag of [dragY] pixels. */
internal fun dragDismissProgress(dragY: Float): Float = (abs(dragY) / FULL_FADE_DRAG_PX).coerceIn(0f, 1f)

/** True when a drag of [dragY] pixels is far enough that letting go should close the viewer. */
internal fun shouldDismissDrag(dragY: Float): Boolean = abs(dragY) >= DISMISS_DRAG_PX

/** Keeps a pan [offset] inside the area an image zoomed to [scale] can actually be moved within, for a view [viewSize] wide/tall. */
internal fun clampPan(offset: Float, scale: Float, viewSize: Float): Float {
    val max = viewSize * (scale - 1f) / 2f
    return if (max <= 0f) 0f else offset.coerceIn(-max, max)
}

@Composable
fun CinematicImageViewer(model: Any?, contentDescription: String?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val reduce = rememberReduceMotion()
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) enter.animateTo(1f, tween(260)) }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    fun springTo(from: Float, to: Float, set: (Float) -> Unit) {
        scope.launch { animate(from, to, animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { v, _ -> set(v) } }
    }

    val fade = dragDismissProgress(dragY)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f * enter.value * (1f - fade)))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                springTo(scale, 1f) { scale = it }
                                springTo(offsetX, 0f) { offsetX = it }
                                springTo(offsetY, 0f) { offsetY = it }
                            } else {
                                springTo(scale, DOUBLE_TAP_ZOOM) { scale = it }
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val down = event.changes.count { it.pressed }
                            if (down >= 2) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                                val pan = event.calculatePan()
                                offsetX = clampPan(offsetX + pan.x, scale, size.width.toFloat())
                                offsetY = clampPan(offsetY + pan.y, scale, size.height.toFloat())
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (down == 1) {
                                val pan = event.calculatePan()
                                if (scale > 1.01f) {
                                    offsetX = clampPan(offsetX + pan.x, scale, size.width.toFloat())
                                    offsetY = clampPan(offsetY + pan.y, scale, size.height.toFloat())
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (abs(pan.y) > abs(pan.x) || dragY != 0f) {
                                    dragY += pan.y
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        // Finger(s) lifted: close if dragged far enough, otherwise settle back.
                        if (shouldDismissDrag(dragY)) {
                            onDismiss()
                        } else if (dragY != 0f) {
                            springTo(dragY, 0f) { dragY = it }
                        }
                        if (scale < 1.01f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val entrance = 0.92f + 0.08f * enter.value
                        val shrink = 1f - 0.12f * fade
                        scaleX = scale * entrance * shrink
                        scaleY = scale * entrance * shrink
                        translationX = offsetX
                        translationY = offsetY + dragY
                        alpha = enter.value
                    },
            )
        }
    }
}
