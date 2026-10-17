package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichBodyTest {
    @Test
    fun `every catalog sticker round trips and has valid data`() {
        val all = StickerCatalog.all
        assertThat(all.map { it.id }.toSet()).hasSize(all.size)
        assertThat(all.all { StickerCatalog.isValidPalette(it.palette) }).isTrue()
        assertThat(all.all { EmojiText.emojiOnlyCount(it.emoji) >= 1 }).isTrue()
        assertThat(all.all { it.captionFa.length <= 24 && it.captionEn.length <= 24 }).isTrue()
        StickerCategory.values().forEach { assertThat(StickerCatalog.inCategory(it)).isNotEmpty() }
        all.forEach {
            val parsed = RichBody.parse(RichBody.encodeSticker(it)) as RichBody.Sticker
            assertThat(parsed.spec.catalogId).isEqualTo(it.id)
        }
    }

    @Test
    fun `custom sticker keeps emoji caption and palette, and encoding is marker-safe`() {
        val body = RichBody.encodeCustomSticker("\uD83D\uDC97", "  a b*c  ", 3)!!
        assertThat(Regex("^[A-Za-z0-9_%:.\\-]+$").matches(body)).isTrue()
        val spec = (RichBody.parse(body) as RichBody.Sticker).spec
        assertThat(spec.emoji).isEqualTo("\uD83D\uDC97")
        assertThat(spec.captionEn).isEqualTo("a b*c")
        assertThat(spec.palette).isEqualTo(3)
        assertThat(spec.catalogId).isNull()
    }

    @Test
    fun `custom sticker rejects bad emoji and palette, tampered markers parse as plain`() {
        assertThat(RichBody.encodeCustomSticker("a", "", 0)).isNull()
        assertThat(RichBody.encodeCustomSticker("\uD83D\uDE0D\uD83D\uDE0D\uD83D\uDE0D\uD83D\uDE0D", "", 0)).isNull()
        assertThat(RichBody.encodeCustomSticker("\u2764\uFE0F", "", 99)).isNull()
        assertThat(RichBody.parse("sticker:v1:c:99:%F0%9F%92%97:x")).isEqualTo(RichBody.Plain)
        assertThat(RichBody.parse("sticker:v1:c:1:abc:x")).isEqualTo(RichBody.Plain)
        assertThat(RichBody.parse("sticker:v1:c:1:%ZZ:x")).isEqualTo(RichBody.Plain)
        assertThat(RichBody.parse("sticker:v1:nope_99")).isEqualTo(RichBody.Plain)
    }

    @Test
    fun `gif marker validates id and size`() {
        val body = RichBody.encodeGif("xT9IgzoKnwFNmISR8I", 200, 150)
        assertThat(body).isEqualTo("gif:v1:xT9IgzoKnwFNmISR8I:200:150")
        assertThat(RichBody.parse(body)).isEqualTo(RichBody.Gif("xT9IgzoKnwFNmISR8I", 200, 150))
        assertThat(RichBody.encodeGif("bad id", 1, 1)).isNull()
        assertThat(RichBody.parse("gif:v1:abc:1:1")).isEqualTo(RichBody.Plain)
        assertThat(RichBody.parse("gif:v1:xT9IgzoKnwFNmISR8I:0:5")).isEqualTo(RichBody.Plain)
        assertThat(RichBody.encodeGif("abcde12345", 99999, 0)).isEqualTo("gif:v1:abcde12345:4000:1")
    }

    @Test
    fun `ordinary text and embedded markers stay plain`() {
        assertThat(RichBody.parse("hello")).isEqualTo(RichBody.Plain)
        assertThat(RichBody.parse(null)).isEqualTo(RichBody.Plain)
        assertThat(RichBody.parse("look: sticker:v1:love_01")).isEqualTo(RichBody.Plain)
        assertThat(RichBody.kindOf("sticker:v1:love_01")).isEqualTo(RichBody.Kind.STICKER)
        assertThat(RichBody.kindOf("gif:v1:abcde12345:10:10")).isEqualTo(RichBody.Kind.GIF)
        assertThat(RichBody.kindOf("hi")).isNull()
    }
}
