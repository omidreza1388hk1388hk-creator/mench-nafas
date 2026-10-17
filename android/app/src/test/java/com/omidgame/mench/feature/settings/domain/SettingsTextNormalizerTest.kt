package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsTextNormalizerTest {

    @Test fun `arabic yeh and kaf fold to persian forms`() {
        assertThat(SettingsTextNormalizer.normalize("كيبورد")).isEqualTo(SettingsTextNormalizer.normalize("کیبورد"))
        assertThat(SettingsTextNormalizer.normalize("علي")).isEqualTo("علی")
    }

    @Test fun `zwnj and tatweel are ignored`() {
        assertThat(SettingsTextNormalizer.normalize("می\u200Cخواهم")).isEqualTo("میخواهم")
        assertThat(SettingsTextNormalizer.normalize("کـــتاب")).isEqualTo("کتاب")
    }

    @Test fun `persian and arabic indic digits become ascii`() {
        assertThat(SettingsTextNormalizer.normalize("۱۲۳")).isEqualTo("123")
        assertThat(SettingsTextNormalizer.normalize("٤٥٦")).isEqualTo("456")
        assertThat(SettingsTextNormalizer.normalize("2 قدم")).isEqualTo(SettingsTextNormalizer.normalize("۲ قدم"))
    }

    @Test fun `case punctuation and whitespace are collapsed`() {
        assertThat(SettingsTextNormalizer.normalize("  Privacy &   Security! ")).isEqualTo("privacy security")
    }

    @Test fun `diacritics are removed`() {
        assertThat(SettingsTextNormalizer.normalize("مُحَمَّد")).isEqualTo("محمد")
    }

    @Test fun `empty and symbol only input gives no tokens`() {
        assertThat(SettingsTextNormalizer.tokens("")).isEmpty()
        assertThat(SettingsTextNormalizer.tokens(" ... ")).isEmpty()
    }

    @Test fun `tokens split on spaces`() {
        assertThat(SettingsTextNormalizer.tokens("قفل  برنامه")).containsExactly("قفل", "برنامه").inOrder()
    }
}
