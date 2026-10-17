package com.omidgame.mench.feature.privacy.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrivacyRulesTest {
    @Test
    fun `presets are recognised and anything else is custom`() {
        assertThat(DisappearingPresets.isPreset(null)).isTrue()
        assertThat(DisappearingPresets.isPreset(0)).isTrue()
        assertThat(DisappearingPresets.isPreset(86_400)).isTrue()
        assertThat(DisappearingPresets.isPreset(120)).isFalse()
    }

    @Test
    fun `presets stay inside the server range`() {
        DisappearingPresets.all.filter { it != DisappearingPresets.OFF }.forEach {
            assertThat(it).isAtLeast(60)
            assertThat(it).isAtMost(31_536_000)
        }
    }

    @Test
    fun `password rules match the server`() {
        assertThat(TwoStepValidation.check("12345", "12345", "")).isEqualTo(TwoStepValidation.Problem.TOO_SHORT)
        assertThat(TwoStepValidation.check("x".repeat(65), "x".repeat(65), "")).isEqualTo(TwoStepValidation.Problem.TOO_LONG)
        assertThat(TwoStepValidation.check("aaaaaaaa", "aaaaaaaa", "")).isEqualTo(TwoStepValidation.Problem.REPEATED)
        assertThat(TwoStepValidation.check("correct horse", "correct hors", "")).isEqualTo(TwoStepValidation.Problem.MISMATCH)
        assertThat(TwoStepValidation.check("secret99", "secret99", "my SECRET99")).isEqualTo(TwoStepValidation.Problem.HINT_CONTAINS_PASSWORD)
        assertThat(TwoStepValidation.check("correct horse", "correct horse", "a phrase")).isNull()
    }
}
