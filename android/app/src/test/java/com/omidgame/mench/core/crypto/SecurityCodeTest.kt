package com.omidgame.mench.core.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecurityCodeTest {
    private val a = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaaaa"
    private val b = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbbbb"

    @Test fun `pair code is the same on both phones`() {
        assertThat(SecurityCode.forPair(a, b)).isEqualTo(SecurityCode.forPair(b, a))
        assertThat(SecurityCode.qrPayload(a, b)).isEqualTo(SecurityCode.qrPayload(b, a))
    }

    @Test fun `pair code has 12 groups of 5 digits`() {
        val groups = SecurityCode.forPair(a, b).split(" ")
        assertThat(groups).hasSize(12)
        assertThat(groups.all { it.length == 5 && it.all(Char::isDigit) }).isTrue()
    }

    @Test fun `single key code has 6 groups and differs per key`() {
        assertThat(SecurityCode.forKey(a).split(" ")).hasSize(6)
        assertThat(SecurityCode.forKey(a)).isNotEqualTo(SecurityCode.forKey(b))
    }

    @Test fun `a swapped key changes the code`() {
        assertThat(SecurityCode.forPair(a, b)).isNotEqualTo(SecurityCode.forPair(a, a + "x"))
    }

    @Test fun `qr match accepts same pair and rejects different pair`() {
        val scanned = SecurityCode.qrPayload(b, a)
        assertThat(SecurityCode.qrMatches(scanned, a, b)).isTrue()
        assertThat(SecurityCode.qrMatches(scanned, a, a + "x")).isFalse()
        assertThat(SecurityCode.qrMatches("garbage", a, b)).isFalse()
    }

    @Test fun `trust policy first use same changed`() {
        assertThat(KeyTrustPolicy.evaluate(null, a)).isEqualTo(KeyTrust.FIRST_USE)
        assertThat(KeyTrustPolicy.evaluate(a, a)).isEqualTo(KeyTrust.SAME)
        assertThat(KeyTrustPolicy.evaluate(a, b)).isEqualTo(KeyTrust.CHANGED)
    }
}
