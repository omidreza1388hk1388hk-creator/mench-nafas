package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrefResetPlanTest {

    private val autoDownload = SettingsResetPlan.prefTargets(SettingsCategory.DATA).single()

    @Test fun `auto download rules and cap are removed but per-chat overrides are kept`() {
        val present = setOf("rule_WIFI_PHOTO", "rule_MOBILE_VIDEO", "max_mobile_mb", "chat_abc123")
        assertThat(SettingsResetPlan.keysToRemove(present, autoDownload))
            .containsExactly("rule_WIFI_PHOTO", "rule_MOBILE_VIDEO", "max_mobile_mb")
    }

    @Test fun `unknown keys are never removed`() {
        assertThat(SettingsResetPlan.keysToRemove(setOf("pin_hash", "pin_salt", "something"), autoDownload)).isEmpty()
    }

    @Test fun `no target lists a whole file or secret key`() {
        val all = SettingsCategory.entries.flatMap { SettingsResetPlan.prefTargets(it) }
        assertThat(all.all { it.exactKeys.isNotEmpty() || it.keyPrefixes.isNotEmpty() }).isTrue()
        val secrets = setOf("pin_hash", "pin_salt", "biometric_enabled")
        assertThat(all.flatMap { it.exactKeys }.intersect(secrets)).isEmpty()
    }

    @Test fun `notification reset removes switches and quiet hours but not chat overrides`() {
        val target = SettingsResetPlan.prefTargets(SettingsCategory.NOTIFICATIONS).single()
        val present = setOf("master_enabled", "type_messages", "type_reminders", "quiet_enabled", "quiet_start", "mute_until_abc", "sound_uri_abc", "chan_rev_abc")
        assertThat(SettingsResetPlan.keysToRemove(present, target))
            .containsExactly("master_enabled", "type_messages", "type_reminders", "quiet_enabled", "quiet_start")
    }

    @Test fun `canReset matches what exists`() {
        assertThat(SettingsResetPlan.canReset(SettingsCategory.DATA)).isTrue()
        assertThat(SettingsResetPlan.canReset(SettingsCategory.SECURITY)).isTrue() // auto-lock delay only
        assertThat(SettingsResetPlan.canReset(SettingsCategory.CALLS)).isTrue()
        assertThat(SettingsResetPlan.canReset(SettingsCategory.ACCOUNT)).isFalse()
        assertThat(SettingsResetPlan.resettableCategories).doesNotContain(SettingsCategory.ACCOUNT)
    }
}
