package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.feature.chat.domain.CanvasStroke

/** Phase 3A — the shared canvas: draw with a finger, the partner's phone updates a moment later. */

private val canvasColors = listOf("#e53935", "#fb8c00", "#fdd835", "#43a047", "#1e88e5", "#8e24aa", "#212121", "#ffffff")
private val canvasWidths = listOf(4f, 9f, 18f)

/** Parses "#rrggbb"; an unreadable color falls back to black rather than crashing the dialog. */
internal fun parseCanvasColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Black)

/** Keeps a point only if it moved far enough (in normalised units) from the last kept one, so strokes stay small on the wire. */
internal fun shouldKeepPoint(lastX: Float, lastY: Float, x: Float, y: Float, minDistance: Float = 0.004f): Boolean {
    val dx = x - lastX
    val dy = y - lastY
    return dx * dx + dy * dy >= minDistance * minDistance
}

private fun DrawScope.drawCanvasStroke(stroke: CanvasStroke) {
    val pts = stroke.points
    if (pts.size < 2) return
    val color = parseCanvasColor(stroke.color)
    val w = stroke.width * density * (size.minDimension / (300f * density))
    if (pts.size == 2) {
        drawCircle(color.copy(alpha = 0.18f), radius = w * 1.2f, center = Offset(pts[0] * size.width, pts[1] * size.height))
        drawCircle(color, radius = w / 2f, center = Offset(pts[0] * size.width, pts[1] * size.height))
        return
    }
    val path = Path().apply {
        moveTo(pts[0] * size.width, pts[1] * size.height)
        var i = 2
        while (i + 1 < pts.size) {
            lineTo(pts[i] * size.width, pts[i + 1] * size.height)
            i += 2
        }
    }
    // Glow: a wider, faint copy underneath makes every line look softly lit (skipped for white ink, which would just look smudged).
    if (color != Color.White) {
        drawPath(path, color.copy(alpha = 0.18f), style = Stroke(width = w * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawPath(path, color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun SharedCanvasDialog(
    strokes: List<CanvasStroke>,
    onDismiss: () -> Unit,
    onAddStrokes: (List<CanvasStroke>) -> Unit,
    onClear: () -> Unit,
) {
    var color by remember { mutableStateOf(canvasColors[0]) }
    var width by remember { mutableStateOf(canvasWidths[1]) }
    var confirmClear by remember { mutableStateOf(false) }
    // The stroke being drawn right now, as flat normalised x,y pairs.
    val live = remember { mutableStateListOf<Float>() }
    var liveVersion by remember { mutableStateOf(0) }
    val paper = Color(0xFFFFFBF5)

    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        dismissButton = { TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.canvas_clear)) } },
        title = { Text("🎨  " + stringResource(R.string.canvas_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(paper)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        .pointerInput(color, width) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                live.clear()
                                live.add((down.position.x / w).coerceIn(0f, 1f))
                                live.add((down.position.y / h).coerceIn(0f, 1f))
                                liveVersion++
                                drag(down.id) { change ->
                                    val x = (change.position.x / w).coerceIn(0f, 1f)
                                    val y = (change.position.y / h).coerceIn(0f, 1f)
                                    if (live.size < 1200 && shouldKeepPoint(live[live.size - 2], live[live.size - 1], x, y)) {
                                        live.add(x)
                                        live.add(y)
                                        liveVersion++
                                    }
                                    change.consume()
                                }
                                if (live.size >= 2) onAddStrokes(listOf(CanvasStroke(color, width, live.toList())))
                                live.clear()
                                liveVersion++
                            }
                        },
                ) {
                    @Suppress("UNUSED_EXPRESSION") liveVersion // re-read so the live stroke redraws
                    strokes.forEach { drawCanvasStroke(it) }
                    if (live.size >= 2) drawCanvasStroke(CanvasStroke(color, width, live.toList()))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    canvasColors.forEach { hex ->
                        Row(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(parseCanvasColor(hex))
                                .border(
                                    if (hex == color) 3.dp else 1.dp,
                                    if (hex == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                )
                                .clickable { color = hex },
                        ) {}
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    canvasWidths.forEach { w ->
                        TextButton(onClick = { width = w }) {
                            Text(if (w == width) "● " + w.toInt() else "○ " + w.toInt())
                        }
                    }
                }
                Text(stringResource(R.string.canvas_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )

    if (confirmClear) {
        MenchDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.canvas_clear_confirm)) },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text(stringResource(R.string.canvas_clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.us_cancel)) } },
        )
    }
}
