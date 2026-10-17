package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.omidgame.mench.core.ui.components.MenchDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import java.io.File
import java.io.FileOutputStream

/** One finished or in-progress line. Points are normalised to 0..1 so the PNG can be rendered at any size. */
private class Stroke2(val color: Color, val width: Float, val points: MutableList<Offset> = mutableListOf())

private val PALETTE = listOf(
    Color(0xFFD6336C), Color(0xFFE5484D), Color(0xFFFF8A00), Color(0xFFFFC107),
    Color(0xFF2E9E5B), Color(0xFF1E88E5), Color(0xFF7E57C2), Color(0xFF212121),
)

/**
 * A little sketch pad for sending hand-drawn notes and doodles. Finger-draw,
 * pick a colour and thickness, undo/clear, then send: the picture is rendered
 * to a 1080x1080 PNG on a soft cream background and sent like any other image.
 */
@Composable
fun DrawingDialog(
    onDismiss: () -> Unit,
    onSend: (contentUri: String, mimeType: String, filename: String) -> Unit,
) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<Stroke2>() }
    var color by remember { mutableStateOf(PALETTE.first()) }
    var width by remember { mutableStateOf(0.012f) }
    var revision by remember { mutableStateOf(0) } // forces a redraw while a stroke grows

    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎨  " + stringResource(R.string.drawing_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(CREAM)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                ) {
                    var size by remember { mutableStateOf(1f) }
                    ComposeCanvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(color, width) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        size = this.size.width.toFloat().coerceAtLeast(1f)
                                        strokes.add(Stroke2(color, width, mutableListOf(Offset(start.x / size, start.y / size))))
                                        revision++
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        strokes.lastOrNull()?.points?.add(Offset(change.position.x / size, change.position.y / size))
                                        revision++
                                    },
                                )
                            },
                    ) {
                        revision // read so the canvas redraws when it changes
                        val w = this.size.width
                        strokes.forEach { stroke ->
                            if (stroke.points.size == 1) {
                                drawCircle(stroke.color, radius = stroke.width * w / 2, center = Offset(stroke.points[0].x * w, stroke.points[0].y * w))
                            } else {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(stroke.points[0].x * w, stroke.points[0].y * w)
                                    stroke.points.drop(1).forEach { lineTo(it.x * w, it.y * w) }
                                }
                                drawPath(path, stroke.color, style = Stroke(width = stroke.width * w, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PALETTE.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(if (swatch == color) 30.dp else 24.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .clickable { color = swatch },
                        )
                    }
                }
                Slider(value = width, onValueChange = { width = it }, valueRange = 0.004f..0.04f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); revision++ } }) {
                        Text(stringResource(R.string.drawing_undo))
                    }
                    TextButton(onClick = { strokes.clear(); revision++ }) { Text(stringResource(R.string.drawing_clear)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    val file = renderToPng(context, strokes)
                    onSend(android.net.Uri.fromFile(file).toString(), "image/png", file.name)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.letters_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_cancel)) } },
    )
}

private val CREAM = Color(0xFFFFF8F0)

private fun renderToPng(context: Context, strokes: List<Stroke2>): File {
    val side = 1080
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(CREAM.toArgb())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    strokes.forEach { stroke ->
        if (stroke.points.size == 1) {
            dot.color = stroke.color.toArgb()
            canvas.drawCircle(stroke.points[0].x * side, stroke.points[0].y * side, stroke.width * side / 2, dot)
        } else {
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.width * side
            val path = Path().apply {
                moveTo(stroke.points[0].x * side, stroke.points[0].y * side)
                stroke.points.drop(1).forEach { lineTo(it.x * side, it.y * side) }
            }
            canvas.drawPath(path, paint)
        }
    }
    val dir = File(context.cacheDir, "drawings").apply { mkdirs() }
    val file = File(dir, "drawing-${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}
