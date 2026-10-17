@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omidgame.mench.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** What the chat should send for one photo: a URI (original or freshly rendered) plus its caption. */
data class PhotoOutgoing(val contentUri: String, val mimeType: String, val filename: String, val caption: String?)

enum class PhotoFilter(val labelRes: Int) {
    NONE(R.string.photo_filter_none),
    MONO(R.string.photo_filter_mono),
    SEPIA(R.string.photo_filter_sepia),
    WARM(R.string.photo_filter_warm),
    COOL(R.string.photo_filter_cool),
    VIVID(R.string.photo_filter_vivid),
    FADE(R.string.photo_filter_fade),
}

/** Crop presets are centered crops (width : height). ORIGINAL keeps the photo's own shape. */
enum class PhotoCrop(val ratio: Float?, val label: String) {
    ORIGINAL(null, "•"),
    SQUARE(1f, "1:1"),
    LANDSCAPE_4_3(4f / 3f, "4:3"),
    PORTRAIT_3_4(3f / 4f, "3:4"),
    WIDE_16_9(16f / 9f, "16:9"),
}

/** A stroke in coordinates normalised to the rotated+cropped image (0..1), so preview and export agree. */
class PhotoStroke(val color: Color, val points: MutableList<Offset> = mutableListOf())

class PhotoDraft(val uri: Uri) {
    var caption by mutableStateOf("")
    var rotation by mutableStateOf(0) // 0 / 90 / 180 / 270
    var crop by mutableStateOf(PhotoCrop.ORIGINAL)
    var filter by mutableStateOf(PhotoFilter.NONE)
    val strokes = mutableStateListOf<PhotoStroke>()
    var text by mutableStateOf("")
    var textPos by mutableStateOf(Offset(0.5f, 0.5f))
    var revision by mutableStateOf(0)

    val isEdited: Boolean
        get() = rotation != 0 || crop != PhotoCrop.ORIGINAL || filter != PhotoFilter.NONE || strokes.isNotEmpty() || text.isNotBlank()
}

private val INK = listOf(Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFD81B60))

private fun filterMatrix(f: PhotoFilter): ColorMatrix? = when (f) {
    PhotoFilter.NONE -> null
    PhotoFilter.MONO -> ColorMatrix().apply { setSaturation(0f) }
    PhotoFilter.SEPIA -> ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
    PhotoFilter.WARM -> ColorMatrix(floatArrayOf(
        1.12f, 0f, 0f, 0f, 8f,
        0f, 1.0f, 0f, 0f, 0f,
        0f, 0f, 0.86f, 0f, -6f,
        0f, 0f, 0f, 1f, 0f,
    ))
    PhotoFilter.COOL -> ColorMatrix(floatArrayOf(
        0.9f, 0f, 0f, 0f, -4f,
        0f, 1.0f, 0f, 0f, 0f,
        0f, 0f, 1.15f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f,
    ))
    PhotoFilter.VIVID -> ColorMatrix().apply { setSaturation(1.55f) }
    PhotoFilter.FADE -> ColorMatrix(floatArrayOf(
        0.85f, 0f, 0f, 0f, 30f,
        0f, 0.85f, 0f, 0f, 30f,
        0f, 0f, 0.85f, 0f, 30f,
        0f, 0f, 0f, 1f, 0f,
    ))
}

/** Decodes [uri] so its longer side is at most [maxSide], honouring the EXIF orientation. */
private fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val orientation = resolver.openInputStream(uri)?.use {
        android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
    } ?: android.media.ExifInterface.ORIENTATION_NORMAL
    val m = Matrix()
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        else -> return raw
    }
    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
}.getOrNull()

/** Rotation + centered crop + colour filter. Strokes and text are NOT applied here (see [renderFinal]). */
private fun renderBase(src: Bitmap, draft: PhotoDraft): Bitmap {
    val rot = Matrix().apply { postRotate(draft.rotation.toFloat()) }
    val rotated = if (draft.rotation == 0) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, rot, true)
    val ratio = draft.crop.ratio
    var x = 0; var y = 0; var w = rotated.width; var h = rotated.height
    if (ratio != null) {
        val current = w.toFloat() / h
        if (current > ratio) { w = (h * ratio).toInt(); x = (rotated.width - w) / 2 } else { h = (w / ratio).toInt(); y = (rotated.height - h) / 2 }
    }
    val out = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    filterMatrix(draft.filter)?.let { paint.colorFilter = ColorMatrixColorFilter(it) }
    Canvas(out).drawBitmap(rotated, android.graphics.Rect(x, y, x + w, y + h), android.graphics.Rect(0, 0, out.width, out.height), paint)
    return out
}

/** Writes the edited photo to the cache as a JPEG and returns the file. */
private fun renderFinal(context: Context, draft: PhotoDraft, original: Boolean): File? {
    val src = decode(context, draft.uri, if (original) 4096 else 2048) ?: return null
    val base = renderBase(src, draft)
    val canvas = Canvas(base)
    val side = min(base.width, base.height).toFloat()
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = side * 0.012f
    }
    draft.strokes.forEach { s ->
        if (s.points.size < 2) return@forEach
        stroke.color = android.graphics.Color.argb((s.color.alpha * 255).toInt(), (s.color.red * 255).toInt(), (s.color.green * 255).toInt(), (s.color.blue * 255).toInt())
        val path = android.graphics.Path().apply {
            moveTo(s.points[0].x * base.width, s.points[0].y * base.height)
            s.points.drop(1).forEach { lineTo(it.x * base.width, it.y * base.height) }
        }
        canvas.drawPath(path, stroke)
    }
    if (draft.text.isNotBlank()) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = side * 0.07f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD; setShadowLayer(side * 0.01f, 0f, 0f, android.graphics.Color.BLACK)
        }
        canvas.drawText(draft.text, draft.textPos.x * base.width, draft.textPos.y * base.height, tp)
    }
    val dir = File(context.cacheDir, "photo-edits").apply { mkdirs() }
    val file = File(dir, "photo-${System.currentTimeMillis()}-${draft.uri.hashCode().toUInt()}.jpg")
    FileOutputStream(file).use { base.compress(Bitmap.CompressFormat.JPEG, if (original) 100 else 88, it) }
    return file
}

private enum class EditTool { NONE, CROP, FILTER, DRAW, TEXT }

/**
 * Full-screen composer for 1–10 photos picked from the gallery: swipe between photos, edit each
 * (rotate, crop, filter, draw, text), caption each, optionally send at original quality.
 */
@Composable
fun PhotoComposerDialog(
    uris: List<Uri>,
    onDismiss: () -> Unit,
    onSend: (List<PhotoOutgoing>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drafts = remember(uris) { uris.map { PhotoDraft(it) } }
    val pager = rememberPagerState(pageCount = { drafts.size })
    var tool by remember { mutableStateOf(EditTool.NONE) }
    var ink by remember { mutableStateOf(INK[2]) }
    var original by remember { mutableStateOf(ChatSettingsStore.current.photoQuality == com.omidgame.mench.feature.settings.domain.PhotoQuality.ORIGINAL) } // Settings Phase 2 default
    var viewOnce by remember { mutableStateOf(ChatSettingsStore.current.viewOnceDefault) } // Settings Phase 2 default
    var sending by remember { mutableStateOf(false) }
    var textDialog by remember { mutableStateOf(false) }
    val current = drafts[pager.currentPage]

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.us_cancel), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                if (drafts.size > 1) Text("${pager.currentPage + 1} / ${drafts.size}", color = Color.White)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { current.rotation = (current.rotation + 90) % 360; current.strokes.clear(); current.text = "" }) {
                    Icon(Icons.Filled.RotateRight, contentDescription = stringResource(R.string.photo_rotate), tint = Color.White)
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = tool != EditTool.DRAW && tool != EditTool.TEXT) { page ->
                PhotoPage(drafts[page], drawing = tool == EditTool.DRAW && page == pager.currentPage, ink = ink, movingText = tool == EditTool.TEXT && page == pager.currentPage)
            }
            // Tool options
            when (tool) {
                EditTool.CROP -> Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    PhotoCrop.values().forEach { c ->
                        FilterChip(selected = current.crop == c, onClick = { current.crop = c; current.strokes.clear() }, label = { Text(c.label) })
                    }
                }
                EditTool.FILTER -> LazyRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(PhotoFilter.values().toList()) { _, f ->
                        FilterChip(selected = current.filter == f, onClick = { current.filter = f }, label = { Text(stringResource(f.labelRes)) })
                    }
                }
                EditTool.DRAW -> Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    INK.forEach { c ->
                        Box(Modifier.size(28.dp).background(c, CircleShape).border(if (c == ink) 3.dp else 1.dp, if (c == ink) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape).clickable { ink = c })
                    }
                    IconButton(onClick = { if (current.strokes.isNotEmpty()) current.strokes.removeAt(current.strokes.lastIndex) }) {
                        Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.photo_undo), tint = Color.White)
                    }
                }
                EditTool.TEXT -> Text(stringResource(R.string.photo_text_drag_hint), color = Color.White, modifier = Modifier.fillMaxWidth().padding(8.dp))
                EditTool.NONE -> {}
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(Icons.Filled.Crop, stringResource(R.string.photo_crop), tool == EditTool.CROP) { tool = if (tool == EditTool.CROP) EditTool.NONE else EditTool.CROP }
                ToolButton(Icons.Filled.Palette, stringResource(R.string.photo_filters), tool == EditTool.FILTER) { tool = if (tool == EditTool.FILTER) EditTool.NONE else EditTool.FILTER }
                ToolButton(Icons.Filled.Edit, stringResource(R.string.photo_draw), tool == EditTool.DRAW) { tool = if (tool == EditTool.DRAW) EditTool.NONE else EditTool.DRAW }
                ToolButton(Icons.Filled.TextFields, stringResource(R.string.photo_text), tool == EditTool.TEXT) {
                    if (current.text.isBlank()) textDialog = true
                    tool = if (tool == EditTool.TEXT) EditTool.NONE else EditTool.TEXT
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = original, onCheckedChange = { original = it })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.photo_send_original), color = Color.White)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = viewOnce, onCheckedChange = { viewOnce = it })
                Spacer(Modifier.width(8.dp))
                Text("👁  " + stringResource(R.string.photo_view_once), color = Color.White)
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = current.caption,
                    onValueChange = { current.caption = it.take(1024) },
                    placeholder = { Text(stringResource(R.string.photo_caption_hint)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = !sending,
                    onClick = {
                        sending = true
                        scope.launch {
                            val out = withContext(Dispatchers.IO) {
                                drafts.mapNotNull { d ->
                                    val caption = d.caption.trim().ifEmpty { null }
                                    if (!d.isEdited && original) {
                                        // Untouched + "original": send the picked file as is, no re-encoding.
                                        val mime = context.contentResolver.getType(d.uri) ?: "image/jpeg"
                                        PhotoOutgoing(d.uri.toString(), mime, "photo-${System.currentTimeMillis()}.${mime.substringAfter('/', "jpg")}", caption)
                                    } else {
                                        renderFinal(context, d, original)?.let { PhotoOutgoing(Uri.fromFile(it).toString(), "image/jpeg", it.name, caption) }
                                    }
                                }
                            }
                            val tagged = if (viewOnce) out.map { it.copy(filename = VIEW_ONCE_PREFIX + it.filename.removePrefix(VIEW_ONCE_PREFIX)) } else out
                            if (tagged.isNotEmpty()) onSend(tagged)
                            onDismiss()
                        }
                    },
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.letters_send), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (textDialog) {
        var draft by remember { mutableStateOf(current.text) }
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = { textDialog = false },
            title = { Text(stringResource(R.string.photo_text)) },
            text = { TextField(value = draft, onValueChange = { draft = it.take(80) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { current.text = draft.trim(); textDialog = false }) { Text(stringResource(R.string.seen_by_close)) } },
        )
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else Color.White)
        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** One photo: the processed preview with live strokes and the draggable text on top. */
@Composable
private fun PhotoPage(draft: PhotoDraft, drawing: Boolean, ink: Color, movingText: Boolean) {
    val context = LocalContext.current
    var source by remember(draft.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(draft.uri) { source = withContext(Dispatchers.IO) { decode(context, draft.uri, 1280) } }
    val preview = remember(source, draft.rotation, draft.crop, draft.filter) { source?.let { renderBase(it, draft) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = preview ?: return@Box
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val imgRatio = bmp.width.toFloat() / bmp.height
            val fit = if (imgRatio > maxWidth / maxHeight) Modifier.fillMaxWidth().aspectRatio(imgRatio) else Modifier.fillMaxHeight().aspectRatio(imgRatio)
            Box(fit) {
                androidx.compose.foundation.Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                ComposeCanvas(
                    Modifier.fillMaxSize()
                        .pointerInput(drawing, movingText, ink) {
                            if (drawing) {
                                detectDragGestures(
                                    onDragStart = { o -> draft.strokes.add(PhotoStroke(ink, mutableListOf(Offset(o.x / size.width, o.y / size.height)))); draft.revision++ },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        draft.strokes.lastOrNull()?.points?.add(Offset(change.position.x / size.width, change.position.y / size.height)); draft.revision++
                                    },
                                )
                            } else if (movingText) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    draft.textPos = Offset((change.position.x / size.width).coerceIn(0.05f, 0.95f), (change.position.y / size.height).coerceIn(0.05f, 0.95f))
                                }
                            }
                        },
                ) {
                    @Suppress("UNUSED_VARIABLE") val tick = draft.revision // redraw while a stroke grows
                    val s = min(size.width, size.height)
                    draft.strokes.forEach { st ->
                        if (st.points.size < 2) return@forEach
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(st.points[0].x * size.width, st.points[0].y * size.height)
                            st.points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                        }
                        drawPath(path, st.color, style = Stroke(width = s * 0.012f, cap = StrokeCap.Round))
                    }
                    if (draft.text.isNotBlank()) {
                        drawContext.canvas.nativeCanvas.apply {
                            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.WHITE; textSize = s * 0.07f; textAlign = Paint.Align.CENTER
                                typeface = Typeface.DEFAULT_BOLD; setShadowLayer(s * 0.01f, 0f, 0f, android.graphics.Color.BLACK)
                            }
                            drawText(draft.text, draft.textPos.x * size.width, draft.textPos.y * size.height, p)
                        }
                    }
                }
            }
        }
    }
}
