package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import com.omidgame.mench.core.webrtc.CallDiagnosticsSnapshot
import com.omidgame.mench.feature.calls.domain.AudioRoute
import com.omidgame.mench.feature.calls.domain.AudioRoutePolicy
import com.omidgame.mench.feature.calls.domain.CallQuality
import com.omidgame.mench.feature.calls.domain.CallQualityTier
import com.omidgame.mench.feature.calls.domain.CallType
import org.junit.Test

class CallSettingsModelTest {
    @Test fun `auto preset changes nothing`() {
        for (tier in CallQualityTier.entries) {
            assertThat(CallQuality.maxVideoBitrateBps(tier, CallQualityPreset.AUTO)).isEqualTo(CallQuality.maxVideoBitrateBps(tier))
        }
    }

    @Test fun `data saver caps every tier at 400k`() {
        assertThat(CallQuality.maxVideoBitrateBps(CallQualityTier.GOOD, CallQualityPreset.DATA_SAVER)).isEqualTo(400_000)
        assertThat(CallQuality.maxVideoBitrateBps(CallQualityTier.POOR, CallQualityPreset.DATA_SAVER)).isEqualTo(220_000) // never raised
    }

    @Test fun `high only lifts the good tier and never raises a weak network`() {
        assertThat(CallQuality.maxVideoBitrateBps(CallQualityTier.GOOD, CallQualityPreset.HIGH)).isEqualTo(2_500_000)
        assertThat(CallQuality.maxVideoBitrateBps(CallQualityTier.FAIR, CallQualityPreset.HIGH)).isEqualTo(600_000)
        assertThat(CallQuality.maxVideoBitrateBps(CallQualityTier.POOR, CallQualityPreset.HIGH)).isEqualTo(220_000)
    }

    @Test fun `voice starts at the ear unless the setting says speaker, headsets still win`() {
        val phone = AudioRoutePolicy.available(hasBluetooth = false, hasWired = false)
        assertThat(AudioRoutePolicy.initial(CallType.VOICE, phone)).isEqualTo(AudioRoute.EARPIECE)
        assertThat(AudioRoutePolicy.initial(CallType.VOICE, phone, voiceOnSpeaker = true)).isEqualTo(AudioRoute.SPEAKER)
        val withWired = AudioRoutePolicy.available(hasBluetooth = false, hasWired = true)
        assertThat(AudioRoutePolicy.initial(CallType.VOICE, withWired, voiceOnSpeaker = true)).isEqualTo(AudioRoute.WIRED)
    }

    @Test fun `defaults are today's behaviour`() {
        val d = CallSettingsSnapshot()
        assertThat(d.qualityPreset).isEqualTo(CallQualityPreset.AUTO)
        assertThat(d.noiseSuppression && d.echoCancellation && d.frontCameraDefault && d.mirrorFrontCamera).isTrue()
        assertThat(d.voiceStartsOnSpeaker).isFalse()
        assertThat(CallQualityPreset.fromStorage("zzz")).isEqualTo(CallQualityPreset.AUTO)
    }

    @Test fun `diagnostics text has numbers only and handles missing values`() {
        val text = CallDiagnosticsText.format(
            CallDiagnosticsSnapshot("VIDEO", 120.0, null, 12.34, "GOOD", 1_500_000, "AUTO", 99L),
        )
        assertThat(text).contains("rtt_ms: 120.0")
        assertThat(text).contains("packet_loss_percent: n/a")
        assertThat(text).contains("jitter_ms: 12.3")
        assertThat(text).contains("video_bitrate_cap_bps: 1500000")
    }
}
