package com.omidgame.mench.feature.calls.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CallLogicTest {
    private fun s(r: Double?, l: Double?, j: Double?) = CallStatsSample(r, l, j)

    @Test
    fun `quality tiers follow rtt, loss and jitter`() {
        assertThat(CallQuality.tier(s(null, null, null))).isEqualTo(CallQualityTier.UNKNOWN)
        assertThat(CallQuality.tier(s(60.0, 0.5, 8.0))).isEqualTo(CallQualityTier.GOOD)
        assertThat(CallQuality.tier(s(250.0, 0.0, 5.0))).isEqualTo(CallQualityTier.FAIR)
        assertThat(CallQuality.tier(s(50.0, 4.0, 5.0))).isEqualTo(CallQualityTier.FAIR)
        assertThat(CallQuality.tier(s(50.0, 12.0, 1.0))).isEqualTo(CallQualityTier.POOR)
        assertThat(CallQuality.tier(s(50.0, 0.0, 90.0))).isEqualTo(CallQualityTier.POOR)
        assertThat(CallQuality.lossPercent(5, 95)).isEqualTo(5.0)
        assertThat(CallQuality.lossPercent(0, 0)).isNull()
    }

    @Test
    fun `bitrate ceilings shrink as quality drops`() {
        val good = CallQuality.maxVideoBitrateBps(CallQualityTier.GOOD)
        val fair = CallQuality.maxVideoBitrateBps(CallQualityTier.FAIR)
        val poor = CallQuality.maxVideoBitrateBps(CallQualityTier.POOR)
        assertThat(good).isGreaterThan(fair)
        assertThat(fair).isGreaterThan(poor)
    }

    @Test
    fun `tracker reacts fast to worse quality and recovers cautiously`() {
        val t = QualityTracker()
        assertThat(t.push(s(50.0, 0.0, 5.0))).isEqualTo(CallQualityTier.GOOD)
        assertThat(t.push(s(500.0, 0.0, 5.0))).isEqualTo(CallQualityTier.GOOD)
        assertThat(t.push(s(500.0, 0.0, 5.0))).isEqualTo(CallQualityTier.POOR)
        assertThat(t.push(s(50.0, 0.0, 5.0))).isEqualTo(CallQualityTier.POOR)
        assertThat(t.push(s(50.0, 0.0, 5.0))).isEqualTo(CallQualityTier.POOR)
        assertThat(t.push(s(50.0, 0.0, 5.0))).isEqualTo(CallQualityTier.GOOD)
        assertThat(t.push(s(null, null, null))).isEqualTo(CallQualityTier.GOOD)
    }

    @Test
    fun `stats parser reads rtt, jitter and loss deltas`() {
        val first = CallStatsParser.parse(
            listOf(
                RawStat("candidate-pair", mapOf("nominated" to true, "currentRoundTripTime" to 0.08)),
                RawStat("inbound-rtp", mapOf("packetsLost" to 2L, "packetsReceived" to 198L, "jitter" to 0.012)),
            ),
            previous = null,
        )
        assertThat(first.first.rttMs).isWithin(0.001).of(80.0)
        assertThat(first.first.jitterMs).isWithin(0.001).of(12.0)
        assertThat(first.first.packetLossPercent).isNull()
        assertThat(first.second).isEqualTo(StatsCounters(2, 198))
        val second = CallStatsParser.parse(
            listOf(RawStat("inbound-rtp", mapOf("packetsLost" to 12L, "packetsReceived" to 288L, "jitter" to 0.02))),
            previous = first.second,
        )
        assertThat(second.first.packetLossPercent).isWithin(0.001).of(10.0)
        assertThat(CallStatsParser.parse(emptyList(), null).first).isEqualTo(CallStatsSample(null, null, null))
    }

    @Test
    fun `reconnect policy is caller-driven with a deadline`() {
        assertThat(ReconnectPolicy.nextAction(1000, 0, true)).isEqualTo(ReconnectPolicy.Action.WAIT)
        assertThat(ReconnectPolicy.nextAction(4000, 0, true)).isEqualTo(ReconnectPolicy.Action.RESTART_ICE)
        assertThat(ReconnectPolicy.nextAction(4000, 0, false)).isEqualTo(ReconnectPolicy.Action.WAIT)
        assertThat(ReconnectPolicy.nextAction(8000, 1, true)).isEqualTo(ReconnectPolicy.Action.WAIT)
        assertThat(ReconnectPolicy.nextAction(12000, 1, true)).isEqualTo(ReconnectPolicy.Action.RESTART_ICE)
        assertThat(ReconnectPolicy.nextAction(20000, 3, true)).isEqualTo(ReconnectPolicy.Action.WAIT)
        assertThat(ReconnectPolicy.nextAction(ReconnectPolicy.GIVE_UP_MS, 0, false)).isEqualTo(ReconnectPolicy.Action.GIVE_UP)
    }

    @Test
    fun `audio route policy prefers headsets and falls back`() {
        val phone = AudioRoutePolicy.available(hasBluetooth = false, hasWired = false)
        assertThat(phone).containsExactly(AudioRoute.EARPIECE, AudioRoute.SPEAKER).inOrder()
        assertThat(AudioRoutePolicy.initial(CallType.VOICE, phone)).isEqualTo(AudioRoute.EARPIECE)
        assertThat(AudioRoutePolicy.initial(CallType.VIDEO, phone)).isEqualTo(AudioRoute.SPEAKER)
        assertThat(AudioRoutePolicy.initial(CallType.VIDEO, AudioRoutePolicy.available(true, false))).isEqualTo(AudioRoute.BLUETOOTH)
        assertThat(AudioRoutePolicy.initial(CallType.VOICE, AudioRoutePolicy.available(true, true))).isEqualTo(AudioRoute.WIRED)
        assertThat(AudioRoutePolicy.afterChange(AudioRoute.BLUETOOTH, CallType.VOICE, phone)).isEqualTo(AudioRoute.EARPIECE)
        assertThat(AudioRoutePolicy.afterChange(AudioRoute.SPEAKER, CallType.VOICE, phone)).isEqualTo(AudioRoute.SPEAKER)
        assertThat(AudioRoutePolicy.usesProximity(AudioRoute.EARPIECE, CallType.VOICE)).isTrue()
        assertThat(AudioRoutePolicy.usesProximity(AudioRoute.SPEAKER, CallType.VOICE)).isFalse()
        assertThat(AudioRoutePolicy.usesProximity(AudioRoute.EARPIECE, CallType.VIDEO)).isFalse()
    }

    private fun entry(id: String, status: CallStatus, out: Boolean, type: CallType, at: String) =
        CallHistoryEntry(id, "c", type, status, at, null, out, "u", null, "")

    @Test
    fun `call log filters and the missed badge`() {
        val list = listOf(
            entry("1", CallStatus.MISSED, false, CallType.VOICE, "2026-10-01T10:00:00Z"),
            entry("2", CallStatus.ENDED, true, CallType.VIDEO, "2026-10-02T10:00:00Z"),
            entry("3", CallStatus.MISSED, false, CallType.VIDEO, "2026-10-03T10:00:00Z"),
            entry("4", CallStatus.MISSED, true, CallType.VOICE, "2026-10-04T10:00:00Z"),
            entry("5", CallStatus.DECLINED, false, CallType.VOICE, "2026-10-05T10:00:00Z"),
        )
        assertThat(CallHistoryLogic.filter(list, CallFilter.MISSED).map { it.id }).containsExactly("1", "3").inOrder()
        assertThat(CallHistoryLogic.filter(list, CallFilter.VIDEO).map { it.id }).containsExactly("2", "3").inOrder()
        assertThat(CallHistoryLogic.filter(list, CallFilter.VOICE).map { it.id }).containsExactly("1", "4", "5").inOrder()
        assertThat(CallHistoryLogic.isHighlightedAsMissed(list[4])).isTrue()
        assertThat(CallHistoryLogic.missedSince(list, java.time.Instant.parse("2026-10-02T00:00:00Z").toEpochMilli())).isEqualTo(1)
        assertThat(CallHistoryLogic.missedSince(list, 0L)).isEqualTo(2)
    }
}
