package com.omidgame.mench.feature.settings.domain

import com.omidgame.mench.core.webrtc.CallDiagnosticsSnapshot
import java.util.Locale

/** Plain-text report for "Copy diagnostics". Numbers only: no names, no message or call content, no keys. */
object CallDiagnosticsText {
    fun format(s: CallDiagnosticsSnapshot): String = buildString {
        appendLine("MENCH call diagnostics")
        appendLine("type: ${s.callType}")
        appendLine("quality: ${s.tier}")
        appendLine("rtt_ms: ${num(s.rttMs)}")
        appendLine("packet_loss_percent: ${num(s.packetLossPercent)}")
        appendLine("jitter_ms: ${num(s.jitterMs)}")
        appendLine("video_bitrate_cap_bps: ${s.bitrateCapBps ?: "n/a"}")
        appendLine("quality_preset: ${s.preset}")
        append("recorded_at_epoch_ms: ${s.recordedAtMillis}")
    }

    private fun num(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
}
