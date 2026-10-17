package com.omidgame.mench.feature.calls.domain

enum class CallQualityTier { UNKNOWN, GOOD, FAIR, POOR }

/** One measurement of the live connection. Any field may be missing (e.g. no audio packets yet). */
data class CallStatsSample(val rttMs: Double?, val packetLossPercent: Double?, val jitterMs: Double?)

/**
 * Phase 2B — turns WebRTC stats into a three-level quality indicator and picks a video bitrate
 * ceiling from it (adaptive bitrate on weak networks). Thresholds follow the usual VoIP guidance
 * (ITU-T G.114-ish: RTT < 200 ms good, loss < 3 % good). Pure Kotlin, unit-tested.
 */
object CallQuality {
    fun tier(s: CallStatsSample): CallQualityTier {
        if (s.rttMs == null && s.packetLossPercent == null && s.jitterMs == null) return CallQualityTier.UNKNOWN
        val rtt = s.rttMs ?: 0.0
        val loss = s.packetLossPercent ?: 0.0
        val jitter = s.jitterMs ?: 0.0
        return when {
            rtt > 400 || loss > 10 || jitter > 80 -> CallQualityTier.POOR
            rtt > 200 || loss > 3 || jitter > 40 -> CallQualityTier.FAIR
            else -> CallQualityTier.GOOD
        }
    }

    /** Video bitrate ceiling (bits/s) for a tier; the sender never exceeds it, WebRTC's own estimator still adapts below it. */
    fun maxVideoBitrateBps(tier: CallQualityTier, preset: com.omidgame.mench.feature.settings.domain.CallQualityPreset): Int =
        preset.apply(maxVideoBitrateBps(tier), maxVideoBitrateBps(CallQualityTier.GOOD))

    fun maxVideoBitrateBps(tier: CallQualityTier): Int = when (tier) {
        CallQualityTier.GOOD -> 1_500_000
        CallQualityTier.UNKNOWN -> 1_000_000
        CallQualityTier.FAIR -> 600_000
        CallQualityTier.POOR -> 220_000
    }

    /** Loss over an interval, from counter deltas. Null when nothing was expected in the interval. */
    fun lossPercent(packetsLostDelta: Long, packetsReceivedDelta: Long): Double? {
        val lost = packetsLostDelta.coerceAtLeast(0)
        val total = lost + packetsReceivedDelta.coerceAtLeast(0)
        return if (total <= 0) null else lost * 100.0 / total
    }
}

/**
 * Debounces the tier so the indicator and the bitrate do not flap: a change needs [required]
 * consecutive samples agreeing, except that getting worse is accepted after fewer samples than
 * getting better (react fast to trouble, recover cautiously).
 */
class QualityTracker(private val requiredToImprove: Int = 3, private val requiredToWorsen: Int = 2) {
    var current: CallQualityTier = CallQualityTier.UNKNOWN
        private set
    private var candidate: CallQualityTier = CallQualityTier.UNKNOWN
    private var streak = 0

    /** Feeds one sample; returns the (possibly changed) stable tier. */
    fun push(sample: CallStatsSample): CallQualityTier {
        val observed = CallQuality.tier(sample)
        if (observed == CallQualityTier.UNKNOWN) return current
        if (current == CallQualityTier.UNKNOWN) {
            current = observed
            candidate = observed
            streak = 0
            return current
        }
        if (observed == current) {
            candidate = current
            streak = 0
            return current
        }
        if (observed == candidate) streak++ else {
            candidate = observed
            streak = 1
        }
        val needed = if (observed.ordinal > current.ordinal) requiredToWorsen else requiredToImprove
        if (streak >= needed) {
            current = observed
            streak = 0
        }
        return current
    }
}
