package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UsSettingsModelTest {
    @Test fun `defaults keep everything visible and unchanged`() {
        val d = UsSettingsSnapshot()
        assertThat(d.moodEnabled && d.questionEnabled && d.wishlistEnabled && d.memoriesEnabled).isTrue()
        assertThat(d.hapticStrength).isEqualTo(HapticStrength.NORMAL)
        assertThat(d.heartsAnimation).isTrue()
    }

    @Test fun `normal strength leaves the original waveform alone`() {
        assertThat(AffectionHaptics.amplitudes(6, HapticStrength.NORMAL)).isNull()
    }

    @Test fun `soft and strong alternate pause and buzz`() {
        val soft = AffectionHaptics.amplitudes(6, HapticStrength.SOFT)!!
        assertThat(soft.toList()).containsExactly(0, 60, 0, 60, 0, 60).inOrder()
        val strong = AffectionHaptics.amplitudes(4, HapticStrength.STRONG)!!
        assertThat(strong.toList()).containsExactly(0, 255, 0, 255).inOrder()
    }

    @Test fun `amplitude array always matches the timings length`() {
        for (n in listOf(4, 6, 8)) assertThat(AffectionHaptics.amplitudes(n, HapticStrength.SOFT)!!.size).isEqualTo(n)
    }

    @Test fun `unknown stored strength falls back to normal`() {
        assertThat(HapticStrength.fromStorage("zzz")).isEqualTo(HapticStrength.NORMAL)
        assertThat(HapticStrength.fromStorage(null)).isEqualTo(HapticStrength.NORMAL)
    }
}
