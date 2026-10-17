package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichTextTest {
    private fun spans(text: String, type: SpanType): List<Pair<String, String?>> =
        RichText.parse(text).let { p -> p.spans.filter { it.type == type }.map { p.plain.substring(it.start, it.end) to it.data } }

    @Test
    fun `style markers are stripped and spanned`() {
        assertThat(RichText.parse("hello **bold** and __it__ ~~gone~~ ||secret|| `code`").plain)
            .isEqualTo("hello bold and it gone secret code")
        assertThat(spans("**bold**", SpanType.BOLD)).containsExactly("bold" to null)
        assertThat(spans("||secret||", SpanType.SPOILER).single().first).isEqualTo("secret")
        assertThat(RichText.parse("`**not bold**`").plain).isEqualTo("**not bold**")
        assertThat(spans("`**not bold**`", SpanType.BOLD)).isEmpty()
    }

    @Test
    fun `unbalanced or empty markers stay literal`() {
        assertThat(RichText.parse("a **b").plain).isEqualTo("a **b")
        assertThat(RichText.parse("**** x").plain).isEqualTo("**** x")
        assertThat(RichText.parse("2 ** 3").plain).isEqualTo("2 ** 3")
    }

    @Test
    fun `links, e-mails and phone numbers are detected`() {
        assertThat(spans("see https://example.com/a?b=1 now", SpanType.URL).single().second).isEqualTo("https://example.com/a?b=1")
        assertThat(spans("go www.example.com.", SpanType.URL)).containsExactly("www.example.com" to "https://www.example.com")
        assertThat(spans("mail me@site.org please", SpanType.EMAIL).single().second).isEqualTo("mailto:me@site.org")
        assertThat(spans("call +98 912 345 6789 now", SpanType.PHONE).single().second).isEqualTo("tel:+989123456789")
        assertThat(spans("\u06F0\u06F9\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9", SpanType.PHONE).single().second).isEqualTo("tel:09123456789")
        assertThat(spans("on 2026-10-05 ok", SpanType.PHONE)).isEmpty()
        assertThat(spans("room 12345", SpanType.PHONE)).isEmpty()
    }

    @Test
    fun `hashtags and mentions`() {
        assertThat(spans("love #us_2026 and #\u0639\u0634\u0642", SpanType.HASHTAG).map { it.second })
            .containsExactly("us_2026", "\u0639\u0634\u0642").inOrder()
        assertThat(spans("a#b", SpanType.HASHTAG)).isEmpty()
        assertThat(spans("hi @sara_k, ok", SpanType.MENTION).single().second).isEqualTo("sara_k")
        assertThat(spans("me@site.org", SpanType.MENTION)).isEmpty()
    }

    @Test
    fun `style markers next to links do not swallow them`() {
        val p = RichText.parse("**see https://a.com/x**")
        assertThat(p.plain).isEqualTo("see https://a.com/x")
        assertThat(p.spans.map { it.type }).containsAtLeast(SpanType.BOLD, SpanType.URL)
        assertThat(RichText.parse("https://x.com/a__b__c").spans.none { it.type == SpanType.ITALIC }).isTrue()
        assertThat(RichText.plainText("**x** y")).isEqualTo("x y")
    }
}

class ContactCardTest {
    @Test
    fun `contact card round trips with cleaned name and phone`() {
        val body = RichBody.encodeContact("  Sara\nK  ", "+98 912-345 6789")!!
        assertThat(RichBody.parse(body)).isEqualTo(RichBody.Contact("Sara K", "+989123456789"))
        assertThat(RichBody.kindOf(body)).isEqualTo(RichBody.Kind.CONTACT)
        assertThat(RichBody.parse(RichBody.encodeContact("\u0633\u0627\u0631\u0627", "\u06F0\u06F9\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9")!!))
            .isEqualTo(RichBody.Contact("\u0633\u0627\u0631\u0627", "09123456789"))
    }

    @Test
    fun `invalid or tampered contact cards are rejected`() {
        assertThat(RichBody.encodeContact("", "123456")).isNull()
        assertThat(RichBody.encodeContact("A", "12")).isNull()
        assertThat(RichBody.encodeContact("A", "abc")).isNull()
        assertThat(RichBody.parse("contact:v1:A:abc")).isEqualTo(RichBody.Plain)
        assertThat(RichBody.parse("contact:v1:A")).isEqualTo(RichBody.Plain)
    }
}
