package com.omidgame.mench.core.media

import java.util.Base64
import kotlin.math.sqrt

/**
 * Compact voice-message waveform: [BARS] samples, each 0..255 (a byte on the wire).
 *
 * The recorder measures MediaRecorder.getMaxAmplitude() (0..32767) every ~60 ms;
 * [fromAmplitudes] turns that long series into a fixed-size, peak-normalised bar
 * set. On the wire / in Room it travels as base64 text (see MessageEntity
 * .attachmentWaveform and the backend attachments.waveform column), which keeps
 * the JSON and the DAO free of binary blobs.
 *
 * Pure Kotlin with java.util.Base64 (API 26 = this app's minSdk), so it is
 * unit-testable on the JVM.
 */
object Waveform {
    const val BARS = 48
    private const val MAX_AMPLITUDE = 32767.0

    /** Raw MediaRecorder amplitude (0..32767) -> 0..255, square-root curve so quiet speech is still visible. */
    fun normalizeAmplitude(raw: Int): Int {
        val clamped = raw.coerceIn(0, MAX_AMPLITUDE.toInt())
        return (sqrt(clamped / MAX_AMPLITUDE) * 255.0).toInt().coerceIn(0, 255)
    }

    /**
     * Buckets [amplitudes] (raw 0..32767 values, any length) into [bars] bars, then scales
     * so the loudest bar is 255. Returns an empty list for a silent / empty recording so the
     * caller can skip sending a flat line.
     */
    fun fromAmplitudes(amplitudes: List<Int>, bars: Int = BARS): List<Int> {
        if (amplitudes.isEmpty() || bars <= 0) return emptyList()
        val normalized = amplitudes.map { normalizeAmplitude(it) }
        val bucketed = List(bars) { bar ->
            // Float bucket edges so every bar covers a share of the series, never an empty slice.
            val from = (bar * normalized.size.toLong() / bars).toInt().coerceIn(0, normalized.size - 1)
            val toExclusive = ((bar + 1) * normalized.size.toLong() / bars).toInt()
                .coerceIn(from + 1, normalized.size)
            var sum = 0
            var peak = 0
            for (i in from until toExclusive) {
                sum += normalized[i]
                if (normalized[i] > peak) peak = normalized[i]
            }
            // Blend of mean and peak: reads livelier than a pure average, calmer than a pure max.
            val mean = sum / (toExclusive - from)
            (mean * 0.5 + peak * 0.5).toInt()
        }
        val max = bucketed.max()
        if (max <= 0) return emptyList()
        return bucketed.map { (it * 255L / max).toInt().coerceIn(0, 255) }
    }

    /** Samples (0..255) -> base64 text, or null if there is nothing worth sending. */
    fun encode(samples: List<Int>): String? {
        if (samples.isEmpty()) return null
        val bytes = ByteArray(samples.size) { samples[it].coerceIn(0, 255).toByte() }
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** base64 text -> samples (0..255); null for absent or malformed input (the UI then draws a neutral fallback). */
    fun decode(encoded: String?): List<Int>? {
        if (encoded.isNullOrEmpty()) return null
        return try {
            val bytes = Base64.getDecoder().decode(encoded)
            if (bytes.isEmpty()) null else bytes.map { it.toInt() and 0xFF }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Deterministic placeholder bars for messages that never had a waveform (older clients); same seed -> same look. */
    fun fallback(seed: String, bars: Int = BARS): List<Int> {
        val rnd = java.util.Random(seed.hashCode().toLong())
        return List(bars) { (64 + rnd.nextInt(160)) }
    }
}
