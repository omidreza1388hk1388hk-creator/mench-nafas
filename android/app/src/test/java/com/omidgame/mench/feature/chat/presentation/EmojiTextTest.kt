package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmojiTextTest {
    @Test
    fun `one to three emoji are big`() {
        assertThat(EmojiText.bigEmojiCount("\u2764\uFE0F")).isEqualTo(1)
        assertThat(EmojiText.bigEmojiCount("\uD83D\uDE02 \uD83D\uDE02")).isEqualTo(2)
        assertThat(EmojiText.bigEmojiCount("\uD83D\uDE0D\uD83D\uDE0D\uD83D\uDE0D")).isEqualTo(3)
    }

    @Test
    fun `four emoji or any text are not big`() {
        assertThat(EmojiText.bigEmojiCount("\uD83D\uDE0D\uD83D\uDE0D\uD83D\uDE0D\uD83D\uDE0D")).isEqualTo(0)
        assertThat(EmojiText.bigEmojiCount("\u0633\u0644\u0627\u0645 \uD83D\uDE0D")).isEqualTo(0)
        assertThat(EmojiText.bigEmojiCount("hello")).isEqualTo(0)
        assertThat(EmojiText.bigEmojiCount("5")).isEqualTo(0)
        assertThat(EmojiText.bigEmojiCount("\uD83D\uDE0D.")).isEqualTo(0)
        assertThat(EmojiText.bigEmojiCount("  ")).isEqualTo(0)
        assertThat(EmojiText.bigEmojiCount(null)).isEqualTo(0)
    }

    @Test
    fun `flags keycaps zwj sequences and skin tones count as one emoji`() {
        assertThat(EmojiText.bigEmojiCount("\uD83C\uDDEE\uD83C\uDDF7")).isEqualTo(1)
        assertThat(EmojiText.bigEmojiCount("\uD83C\uDDEE\uD83C\uDDF7\uD83C\uDDFA\uD83C\uDDF8")).isEqualTo(2)
        assertThat(EmojiText.bigEmojiCount("5\uFE0F\u20E3")).isEqualTo(1)
        assertThat(EmojiText.bigEmojiCount("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67")).isEqualTo(1)
        assertThat(EmojiText.bigEmojiCount("\uD83D\uDC4D\uD83C\uDFFD")).isEqualTo(1)
    }

    @Test
    fun `stray joiners and modifiers do not count`() {
        assertThat(EmojiText.bigEmojiCount("\u200D")).isEqualTo(0)
        assertThat(EmojiText.bigEmojiCount("\uD83C\uDFFD")).isEqualTo(0)
    }
}
