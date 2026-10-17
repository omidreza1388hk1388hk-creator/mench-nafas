package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoundVideoTest {
    @Test
    fun `filename convention identifies round videos only`() {
        val name = RoundVideo.filename(1_700_000_000_000L)
        assertThat(name).isEqualTo("round-video-1700000000000.mp4")
        assertThat(RoundVideo.isRoundFilename(name)).isTrue()
        assertThat(RoundVideo.isRoundFilename("holiday.mp4")).isFalse()
        assertThat(RoundVideo.isRoundFilename("round-video-1.mov")).isFalse()
        assertThat(RoundVideo.isRoundFilename(null)).isFalse()
    }

    @Test
    fun `accidental taps are discarded and progress is clamped`() {
        assertThat(RoundVideo.isLongEnough(999)).isFalse()
        assertThat(RoundVideo.isLongEnough(1_000)).isTrue()
        assertThat(RoundVideo.progress(0)).isEqualTo(0f)
        assertThat(RoundVideo.progress(30_000)).isEqualTo(0.5f)
        assertThat(RoundVideo.progress(120_000)).isEqualTo(1f)
    }

    @Test
    fun `cover size fills the circle without empty bars`() {
        val (w, h) = RoundVideo.coverSize(200f, 480, 640)
        assertThat(w).isEqualTo(200f)
        assertThat(h).isWithin(0.01f).of(266.67f)
        val (lw, lh) = RoundVideo.coverSize(200f, 640, 480)
        assertThat(lh).isEqualTo(200f)
        assertThat(lw).isWithin(0.01f).of(266.67f)
        assertThat(RoundVideo.coverSize(200f, null, 480)).isEqualTo(200f to 200f)
        assertThat(RoundVideo.coverSize(200f, 0, 0)).isEqualTo(200f to 200f)
    }

    @Test
    fun `time formatting`() {
        assertThat(RoundVideo.formatTime(0)).isEqualTo("0:00")
        assertThat(RoundVideo.formatTime(65_000)).isEqualTo("1:05")
        assertThat(RoundVideo.formatTime(-5)).isEqualTo("0:00")
    }
}
