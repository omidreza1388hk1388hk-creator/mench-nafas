package com.omidgame.mench.core.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Phase 1B leftover: H.264/AAC transcoding of large videos before upload (Media3 Transformer).
 * Scales the longer edge down to [MAX_LONG_EDGE]; the Outbox worker reports [onProgress] through
 * [UploadProgress] so the bubble shows a progress ring while it works, and the user can cancel.
 */
@OptIn(UnstableApi::class)
object VideoTranscoder {
    const val MAX_LONG_EDGE = 1280

    /** Worth transcoding only when the picture is bigger than the cap; small clips are uploaded as they are. */
    fun needsTranscode(widthPx: Int?, heightPx: Int?): Boolean {
        val w = widthPx ?: return false
        val h = heightPx ?: return false
        return maxOf(w, h) > MAX_LONG_EDGE
    }

    /** Returns true when [target] was written successfully. Never throws; false means "upload the original". */
    suspend fun transcode(
        context: Context,
        source: File,
        target: File,
        widthPx: Int,
        heightPx: Int,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.Main) {
        // Transformer must be created and driven from a thread with a Looper, hence Main.
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            lateinit var poll: Runnable
            val transformer = runCatching {
                Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(poll)
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            handler.removeCallbacks(poll)
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()
            }.getOrNull()
            if (transformer == null) {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            poll = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    if (transformer.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                    }
                    handler.postDelayed(this, 300)
                }
            }
            // media3 1.4.x has no width-only Presentation factory, so the long
            // edge is capped explicitly and the short edge follows the source
            // aspect ratio (kept even, as H.264 encoders require).
            val safeW = widthPx.coerceAtLeast(2)
            val safeH = heightPx.coerceAtLeast(2)
            val resize = if (safeH >= safeW) {
                Presentation.createForHeight(minOf(safeH, MAX_LONG_EDGE))
            } else {
                val outW = minOf(safeW, MAX_LONG_EDGE)
                val outH = ((outW.toLong() * safeH / safeW).toInt() and 1.inv()).coerceAtLeast(2)
                Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT)
            }
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
                .setEffects(Effects(emptyList(), listOf(resize)))
                .build()
            val started = runCatching { transformer.start(edited, target.absolutePath) }.isSuccess
            if (!started) {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            handler.post(poll)
            cont.invokeOnCancellation {
                handler.removeCallbacks(poll)
                handler.post { runCatching { transformer.cancel() } }
            }
        }
    }
}
