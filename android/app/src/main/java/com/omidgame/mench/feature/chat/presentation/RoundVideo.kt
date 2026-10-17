package com.omidgame.mench.feature.chat.presentation

import kotlin.math.max

/**
 * Phase 2A — round video messages ("video notes"). They are ordinary video attachments (same
 * upload, thumbnail and delivery pipeline as any video); the only thing that makes one round is
 * its file name, so no backend change is needed and old app versions show a normal video.
 * Pure Kotlin, unit-tested on the JVM.
 */
object RoundVideo {
    const val MAX_DURATION_MS = 60_000L
    const val MIN_DURATION_MS = 1_000L
    private const val FILENAME_PREFIX = "round-video"

    fun filename(timestampMillis: Long): String = "$FILENAME_PREFIX-$timestampMillis.mp4"

    fun isRoundFilename(name: String?): Boolean = name != null && name.startsWith(FILENAME_PREFIX) && name.endsWith(".mp4")

    /** A take shorter than this is treated as an accidental tap and discarded. */
    fun isLongEnough(durationMs: Long): Boolean = durationMs >= MIN_DURATION_MS

    /** 0..1 progress of the recording ring. */
    fun progress(elapsedMs: Long): Float = (elapsedMs.toFloat() / MAX_DURATION_MS).coerceIn(0f, 1f)

    /**
     * Size (in the same unit as [circle]) a video of [videoWidth] x [videoHeight] must be drawn at so
     * that it fully covers a circle/square of side [circle] with no empty bars ("center crop").
     * Unknown or invalid dimensions fall back to a square.
     */
    fun coverSize(circle: Float, videoWidth: Int?, videoHeight: Int?): Pair<Float, Float> {
        if (videoWidth == null || videoHeight == null || videoWidth <= 0 || videoHeight <= 0) return circle to circle
        val shorter = max(1, minOf(videoWidth, videoHeight)).toFloat()
        val scale = circle / shorter
        return videoWidth * scale to videoHeight * scale
    }

    /** m:ss for the duration chip and the live timer. */
    fun formatTime(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
