package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecurityCheckupTest {

    private val none = SecurityFacts(false, false, false, false, false, false)
    private val all = SecurityFacts(true, true, true, true, true, true)

    @Test fun `nothing done scores zero of six`() {
        val r = SecurityCheckup.evaluate(none)
        assertThat(r.score).isEqualTo(0)
        assertThat(r.total).isEqualTo(6)
        assertThat(r.missing).hasSize(6)
    }

    @Test fun `everything done is perfect`() {
        val r = SecurityCheckup.evaluate(all)
        assertThat(r.score).isEqualTo(6)
        assertThat(r.isPerfect).isTrue()
        assertThat(r.missing).isEmpty()
    }

    @Test fun `five of six reports exactly what is missing`() {
        val r = SecurityCheckup.evaluate(all.copy(twoStepEnabled = false))
        assertThat(r.score).isEqualTo(5)
        assertThat(r.missing).containsExactly(SecurityCheckItem.TWO_STEP)
    }

    @Test fun `missing items are ordered by impact`() {
        val r = SecurityCheckup.evaluate(none)
        assertThat(r.missing.first()).isEqualTo(SecurityCheckItem.SCREEN_LOCK)
        assertThat(r.missing.last()).isEqualTo(SecurityCheckItem.DEVICES)
    }

    @Test fun `missing backup needs attention only when e2ee is in use`() {
        val noBackup = all.copy(backupExists = false)
        assertThat(SecurityCheckup.evaluate(noBackup).needsAttention(noBackup.copy(anyE2eeChat = false))).isFalse()
        assertThat(SecurityCheckup.evaluate(noBackup).needsAttention(noBackup.copy(anyE2eeChat = true))).isTrue()
    }

    @Test fun `missing screen lock always needs attention`() {
        val f = all.copy(deviceScreenLockSet = false)
        assertThat(SecurityCheckup.evaluate(f).needsAttention(f)).isTrue()
    }

    @Test fun `missing two step alone does not raise a badge`() {
        val f = all.copy(twoStepEnabled = false)
        assertThat(SecurityCheckup.evaluate(f).needsAttention(f)).isFalse()
    }
}
