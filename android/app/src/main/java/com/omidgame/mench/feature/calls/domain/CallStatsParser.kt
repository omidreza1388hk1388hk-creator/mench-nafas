package com.omidgame.mench.feature.calls.domain

/** One entry of a WebRTC stats report, reduced to what the parser reads (so this stays pure Kotlin). */
data class RawStat(val type: String, val members: Map<String, Any?>)

/** Cumulative inbound packet counters at the time of a sample; the next sample's loss is computed from the difference. */
data class StatsCounters(val lost: Long, val received: Long)

/**
 * Phase 2B — extracts RTT, loss and jitter from `RTCStatsReport` entries.
 * RTT comes from the nominated/succeeded candidate pair (seconds -> ms), loss from the change in
 * inbound-rtp packet counters since the previous sample, jitter from the worst inbound stream.
 */
object CallStatsParser {
    fun parse(stats: List<RawStat>, previous: StatsCounters?): Pair<CallStatsSample, StatsCounters> {
        var rttMs: Double? = null
        var lost = 0L
        var received = 0L
        var jitterMs: Double? = null
        var sawInbound = false

        for (stat in stats) {
            when (stat.type) {
                "candidate-pair" -> {
                    val selected = stat.members["nominated"] == true || stat.members["state"] == "succeeded"
                    val rttSeconds = number(stat.members["currentRoundTripTime"])
                    if (selected && rttSeconds != null && rttSeconds > 0.0) {
                        val ms = rttSeconds * 1000.0
                        rttMs = if (rttMs == null) ms else minOf(rttMs!!, ms)
                    }
                }
                "inbound-rtp" -> {
                    sawInbound = true
                    lost += number(stat.members["packetsLost"])?.toLong() ?: 0L
                    received += number(stat.members["packetsReceived"])?.toLong() ?: 0L
                    val jitterSeconds = number(stat.members["jitter"])
                    if (jitterSeconds != null) {
                        val ms = jitterSeconds * 1000.0
                        jitterMs = if (jitterMs == null) ms else maxOf(jitterMs!!, ms)
                    }
                }
            }
        }
        val counters = StatsCounters(lost, received)
        val loss = if (sawInbound && previous != null) {
            CallQuality.lossPercent(lost - previous.lost, received - previous.received)
        } else {
            null
        }
        return CallStatsSample(rttMs, loss, jitterMs) to counters
    }

    private fun number(value: Any?): Double? = (value as? Number)?.toDouble()
}
