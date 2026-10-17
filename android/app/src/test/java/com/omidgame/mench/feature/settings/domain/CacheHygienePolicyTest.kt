package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CacheHygienePolicyTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun `file is expired after the keep period`() {
        val now = 100 * day
        assertThat(CacheHygienePolicy.isExpired(now - 23 * 60 * 60 * 1000, now, 1)).isFalse()
        assertThat(CacheHygienePolicy.isExpired(now - day, now, 1)).isTrue()
        assertThat(CacheHygienePolicy.isExpired(now - 6 * day, now, 7)).isFalse()
        assertThat(CacheHygienePolicy.isExpired(now - 30 * day, now, 30)).isTrue()
    }

    @Test fun `unknown timestamps are kept`() {
        assertThat(CacheHygienePolicy.isExpired(0L, 100 * day, 1)).isFalse()
    }

    @Test fun `invalid keep days fall back to the default`() {
        assertThat(CacheHygienePolicy.normalizeKeepDays(0)).isEqualTo(1)
        assertThat(CacheHygienePolicy.normalizeKeepDays(5)).isEqualTo(1)
        assertThat(CacheHygienePolicy.normalizeKeepDays(30)).isEqualTo(30)
    }
}
