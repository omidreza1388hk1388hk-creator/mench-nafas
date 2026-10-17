package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLanguageTest {
    @Test fun `system has no forced tag`() = assertThat(AppLanguage.SYSTEM.languageTag).isNull()
    @Test fun `persian and english tags`() {
        assertThat(AppLanguage.PERSIAN.languageTag).isEqualTo("fa")
        assertThat(AppLanguage.ENGLISH.languageTag).isEqualTo("en")
    }
    @Test fun `only persian is rtl`() {
        assertThat(AppLanguage.PERSIAN.isRtl).isTrue()
        assertThat(AppLanguage.ENGLISH.isRtl).isFalse()
        assertThat(AppLanguage.SYSTEM.isRtl).isFalse()
    }
    @Test fun `storage round trip with safe default`() {
        AppLanguage.entries.forEach { assertThat(AppLanguage.fromStorage(it.storageValue)).isEqualTo(it) }
        assertThat(AppLanguage.fromStorage("zz")).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromStorage(null)).isEqualTo(AppLanguage.SYSTEM)
    }
}
