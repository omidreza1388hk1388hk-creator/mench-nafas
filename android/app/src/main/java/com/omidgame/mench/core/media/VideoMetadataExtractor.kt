package com.omidgame.mench.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class VideoMetadata(
    val durationMs: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    /** Path to a locally-saved JPEG frame in app-private storage, or null if frame extraction failed — a video still sends without one (see AttachmentsService's degraded-but-valid handling). */
    val thumbnailLocalPath: String?,
)

/**
 * Extracts everything the server needs from a video client-side
 * (duration, dimensions, a thumbnail frame) via MediaMetadataRetriever —
 * this is what lets AttachmentsService avoid running any video processing
 * of its own (no ffmpeg dependency server-side; see its doc comment).
 */
@Singleton
class VideoMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun extract(contentUri: String): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(contentUri))

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            // Raw encoded width/height — NOT adjusted for
            // METADATA_KEY_VIDEO_ROTATION. A portrait video recorded in
            // landscape sensor orientation (common on phones) reports
            // swapped width/height here versus how it visually displays.
            // Correcting for rotation is a real, known gap, not silently
            // assumed handled — worth fixing before this dimension data
            // is used for anything more than a rough aspect-ratio hint.
            val widthPx = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val heightPx = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()

            val frameBitmap: Bitmap? = try {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                null
            }

            val thumbnailPath = frameBitmap?.let { saveFrameAsJpeg(it) }

            VideoMetadata(durationMs, widthPx, heightPx, thumbnailPath)
        } finally {
            retriever.release()
        }
    }

    private fun saveFrameAsJpeg(bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, "attachment_cache").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }
}
