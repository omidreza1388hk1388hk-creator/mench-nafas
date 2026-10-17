package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GifQueryTest {
    @Test
    fun `persian terms map to english provider queries`() {
        assertThat(GifQuery.toProviderQuery("\u0639\u0634\u0642")).isEqualTo("love")
        assertThat(GifQuery.toProviderQuery("\u062F\u0648\u0633\u062A\u062A \u062F\u0627\u0631\u0645")).isEqualTo("i love you")
        assertThat(GifQuery.toProviderQuery("\u0634\u0628 \u0628\u062E\u06CC\u0631")).isEqualTo("good night")
        assertThat(GifQuery.toProviderQuery("\u0628\u063A\u0644 cute")).isEqualTo("hug cute")
    }

    @Test
    fun `english and unknown terms pass through, blank gives empty`() {
        assertThat(GifQuery.toProviderQuery("romantic couple")).isEqualTo("romantic couple")
        assertThat(GifQuery.toProviderQuery("\u067E\u06CC\u062A\u0632\u0627")).isEqualTo("\u067E\u06CC\u062A\u0632\u0627")
        assertThat(GifQuery.toProviderQuery("   ")).isEqualTo("")
        assertThat(GifQuery.toProviderQuery("  love    you ")).isEqualTo("love you")
    }

    @Test
    fun `arabic letters and zwnj are normalised`() {
        assertThat(GifQuery.toProviderQuery("\u062F\u0644\u062A\u0646\u06AF\u064A")).isEqualTo("miss you")
        assertThat(GifQuery.toProviderQuery("\u062F\u0644\u0645\u200C\u062A\u0646\u06AF")).isEqualTo("miss you")
    }

    @Test
    fun `topics are complete and the default is romantic`() {
        assertThat(GifQuery.topics.all { it.query.isNotBlank() && it.labelFa.isNotBlank() && it.labelEn.isNotBlank() }).isTrue()
        assertThat(GifQuery.defaultTopic.query).isEqualTo("romantic couple")
    }
}
