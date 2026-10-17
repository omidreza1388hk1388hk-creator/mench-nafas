package com.omidgame.mench.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WaveformTest {
    @Test
    fun `normalizeAmplitude maps the raw range onto 0 to 255 and clamps`() {
        assertThat(Waveform.normalizeAmplitude(0)).isEqualTo(0)
        assertThat(Waveform.normalizeAmplitude(32767)).isEqualTo(255)
        assertThat(Waveform.normalizeAmplitude(99999)).isEqualTo(255)
        assertThat(Waveform.normalizeAmplitude(-5)).isEqualTo(0)
    }

    @Test
    fun `fromAmplitudes returns fixed-size bars scaled so the loudest is 255`() {
        val bars = Waveform.fromAmplitudes(List(200) { it * 100 })
        assertThat(bars).hasSize(Waveform.BARS)
        assertThat(bars.max()).isEqualTo(255)
        assertThat(bars.first()).isLessThan(bars.last())
    }

    @Test
    fun `fromAmplitudes handles fewer samples than bars`() {
        val bars = Waveform.fromAmplitudes(listOf(1000, 20000, 500))
        assertThat(bars).hasSize(Waveform.BARS)
        assertThat(bars.max()).isEqualTo(255)
    }

    @Test
    fun `silent or empty input gives no waveform`() {
        assertThat(Waveform.fromAmplitudes(emptyList())).isEmpty()
        assertThat(Waveform.fromAmplitudes(List(100) { 0 })).isEmpty()
    }

    @Test
    fun `encode and decode round trip`() {
        val bars = Waveform.fromAmplitudes(List(120) { (it * 37) % 32000 })
        assertThat(Waveform.decode(Waveform.encode(bars))).isEqualTo(bars)
    }

    @Test
    fun `decode tolerates absent or malformed input`() {
        assertThat(Waveform.decode(null)).isNull()
        assertThat(Waveform.decode("")).isNull()
        assertThat(Waveform.decode("!!!not base64!!!")).isNull()
        assertThat(Waveform.encode(emptyList())).isNull()
    }

    @Test
    fun `fallback is deterministic per seed and in range`() {
        assertThat(Waveform.fallback("a")).isEqualTo(Waveform.fallback("a"))
        assertThat(Waveform.fallback("a")).isNotEqualTo(Waveform.fallback("b"))
        assertThat(Waveform.fallback("a").all { it in 0..255 }).isTrue()
    }
}
