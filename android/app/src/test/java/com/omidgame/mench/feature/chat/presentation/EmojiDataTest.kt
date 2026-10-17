package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmojiDataTest {
    @Test
    fun `dataset is large, de-duplicated and every entry is a real emoji`() {
        val all = EmojiData.all
        assertThat(all.size).isGreaterThan(400)
        assertThat(all.map { it.base }.toSet()).hasSize(all.size)
        assertThat(all.filter { EmojiText.emojiOnlyCount(it.base) < 1 }).isEmpty()
    }

    @Test
    fun `every real category has entries`() {
        EmojiCategory.values().filter { it != EmojiCategory.RECENT }.forEach {
            assertThat(EmojiData.categories[it].orEmpty()).isNotEmpty()
        }
    }

    @Test
    fun `search matches English and Persian keywords and normalises Arabic letters`() {
        assertThat(EmojiData.search("thumbs").map { it.base }).contains("\uD83D\uDC4D")
        assertThat(EmojiData.search("\u06AF\u0631\u0628\u0647").map { it.base }).contains("\uD83D\uDC31")
        // Arabic kaf/yeh typed on an Arabic keyboard still finds the Persian keyword.
        assertThat(EmojiData.search("\u0643\u062A\u0627\u0628").map { it.base }).contains("\uD83D\uDCDA")
        assertThat(EmojiData.search("   ")).isEmpty()
        assertThat(EmojiData.search("zzzzqq")).isEmpty()
    }

    @Test
    fun `skin tone is inserted after the base and only for supporting emoji`() {
        val thumbs = EmojiData.all.first { it.base == "\uD83D\uDC4D" }
        assertThat(EmojiData.withTone(thumbs, 0)).isEqualTo("\uD83D\uDC4D")
        assertThat(EmojiData.withTone(thumbs, 3)).isEqualTo("\uD83D\uDC4D\uD83C\uDFFD")
        val victory = EmojiData.all.first { it.base == "\u270C\uFE0F" }
        assertThat(EmojiData.withTone(victory, 2)).isEqualTo("\u270C\uD83C\uDFFC")
        val heart = EmojiData.all.first { it.base == "\u2764\uFE0F" }
        assertThat(EmojiData.withTone(heart, 4)).isEqualTo("\u2764\uFE0F")
        assertThat(EmojiText.bigEmojiCount(EmojiData.withTone(thumbs, 5))).isEqualTo(1)
    }
}
