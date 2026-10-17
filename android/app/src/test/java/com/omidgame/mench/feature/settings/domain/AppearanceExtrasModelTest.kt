package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppearanceExtrasModelTest {
    @Test fun `volume is clamped and the default matches the old fixed level`() {
        assertThat(SoundSettingsRules.clampVolume(0f)).isEqualTo(0.1f)
        assertThat(SoundSettingsRules.clampVolume(5f)).isEqualTo(1f)
        assertThat(SoundSettingsRules.DEFAULT_VOLUME).isEqualTo(0.7f)
    }

    @Test fun `event keys are derived from sound names`() {
        assertThat(SoundSettingsRules.eventKey("SENT")).isEqualTo("event_sent")
        assertThat(SoundSettingsRules.eventKey("CONFIRM")).isEqualTo("event_confirm")
    }

    @Test fun `a sound needs both switches`() {
        assertThat(SoundSettingsRules.shouldPlay(true, true)).isTrue()
        assertThat(SoundSettingsRules.shouldPlay(false, true)).isFalse()
        assertThat(SoundSettingsRules.shouldPlay(true, false)).isFalse()
    }

    @Test fun `text weight falls back to regular`() {
        assertThat(TextWeightChoice.fromStorage("medium")).isEqualTo(TextWeightChoice.MEDIUM)
        assertThat(TextWeightChoice.fromStorage("zzz")).isEqualTo(TextWeightChoice.REGULAR)
        assertThat(TextWeightChoice.fromStorage(null)).isEqualTo(TextWeightChoice.REGULAR)
    }
}
