package com.omidgame.mench.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 1B: shrinks a picked photo before it is queued for upload.
 *
 * - Longest edge is capped at [maxEdgePx] (1600 by default, 1280 when the file is already big
 *   and the device is memory-constrained callers can pass a smaller value).
 * - JPEG quality adapts to the source size: heavy photos are squeezed harder.
 * - EXIF rotation is baked into the pixels so the result displays upright everywhere.
 * - Images with transparency stay PNG; everything else becomes JPEG.
 * - If the result would not be smaller than the original, the original is kept untouched.
 *
 * Pure file-in / file-out helper (no Android Context, no Hilt), so it can be called from the
 * repository without changing any constructor.
 */
object ImageCompressor {

    data class Result(
        val file: File,
        val mimeType: String,
        val extension: String,
        val widthPx: Int,
        val heightPx: Int,
        val compressed: Boolean,
    )

    private const val DEFAULT_MAX_EDGE = 1600
    private const val MIN_BYTES_WORTH_COMPRESSING = 150_000L

    fun compress(source: File, maxEdgePx: Int = DEFAULT_MAX_EDGE): Result? {
        if (!source.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            val orientation = readOrientation(source)
            val swapped = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            val upW = if (swapped) srcH else srcW
            val upH = if (swapped) srcW else srcH

            val longest = max(upW, upH)
            val alreadySmall = longest <= maxEdgePx && source.length() < MIN_BYTES_WORTH_COMPRESSING
            if (alreadySmall && orientation == ExifInterface.ORIENTATION_NORMAL) {
                return Result(source, bounds.outMimeType ?: "image/jpeg", extensionFor(bounds.outMimeType), upW, upH, false)
            }

            // Decode at a power-of-two sample size first (cheap, low memory), then do the exact resize.
            var sample = 1
            while (longest / (sample * 2) >= maxEdgePx) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(source.absolutePath, decodeOpts) ?: return null

            val scale = min(1f, maxEdgePx.toFloat() / max(decoded.width, decoded.height).toFloat())
            val matrix = Matrix().apply {
                applyExifTransform(this, orientation)
                if (scale < 1f) postScale(scale, scale)
            }
            val finalBitmap = if (matrix.isIdentity) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }
            if (finalBitmap !== decoded) decoded.recycle()

            val hasAlpha = finalBitmap.hasAlpha() && bounds.outMimeType == "image/png"
            val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = when {
                source.length() > 6_000_000L -> 72
                source.length() > 3_000_000L -> 78
                source.length() > 1_000_000L -> 82
                else -> 86
            }
            val ext = if (hasAlpha) ".png" else ".jpg"
            val outFile = File(source.parentFile, source.nameWithoutExtension + "_c" + ext)
            outFile.outputStream().use { finalBitmap.compress(format, quality, it) }
            val outW = finalBitmap.width
            val outH = finalBitmap.height
            finalBitmap.recycle()

            if (outFile.length() >= source.length() && orientation == ExifInterface.ORIENTATION_NORMAL) {
                outFile.delete()
                return Result(source, bounds.outMimeType ?: "image/jpeg", extensionFor(bounds.outMimeType), upW, upH, false)
            }
            source.delete()
            Result(outFile, if (hasAlpha) "image/png" else "image/jpeg", ext, outW, outH, true)
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun readOrientation(file: File): Int = try {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun applyExifTransform(m: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> Unit
        }
    }

    private fun extensionFor(mime: String?): String = when (mime) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        else -> ".jpg"
    }
}
