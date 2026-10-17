package com.omidgame.mench.feature.chat.presentation

/**
 * Phase 2A — message text formatting, mentions, hashtags and clickable phone numbers / links / e-mails.
 *
 * Typed markers (Telegram-style): `**bold**`, `__italic__`, `~~strike~~`, `||spoiler||`, `` `code` ``.
 * Detected automatically: web links, e-mail addresses, phone numbers, #hashtags, @mentions.
 * The markers never reach the server as anything special — the stored text is exactly what was typed;
 * only the rendering changes (so old app versions show the raw markers, harmlessly).
 *
 * [parse] strips the markers and returns the plain text plus spans over it. Spans may overlap
 * (bold link). Pure Kotlin, unit-tested on the JVM.
 */
enum class SpanType { BOLD, ITALIC, STRIKE, CODE, SPOILER, URL, EMAIL, PHONE, HASHTAG, MENTION }

/** A styled / clickable range of the *plain* text. [data] is the link target (URL, mailto, tel, tag, name). */
data class TextSpan(val start: Int, val end: Int, val type: SpanType, val data: String? = null)

data class ParsedText(val plain: String, val spans: List<TextSpan>)

object RichText {
    private const val MAX_DEPTH = 3
    private const val MAX_LENGTH = 8_000

    private class Delimiter(val token: String, val type: SpanType)

    // Longest/most specific first: "**" must be tried before a lone "*" would be (there is none), backtick is single.
    private val delimiters = listOf(
        Delimiter("**", SpanType.BOLD),
        Delimiter("__", SpanType.ITALIC),
        Delimiter("~~", SpanType.STRIKE),
        Delimiter("||", SpanType.SPOILER),
        Delimiter("`", SpanType.CODE),
    )

    private val urlRegex = Regex("""(?i)(?:https?://|www\.)[^\s<>"']+""")
    private val emailRegex = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}""")
    private val phoneRegex = Regex("""(?<![\w+])\+?[0-9\u06F0-\u06F9\u0660-\u0669](?:[0-9\u06F0-\u06F9\u0660-\u0669 \-()]{5,19})[0-9\u06F0-\u06F9\u0660-\u0669](?![\w])""")
    private val hashtagRegex = Regex("""(?<![\p{L}\p{N}_&#])#[\p{L}\p{N}_\u200C]{1,50}""")
    private val mentionRegex = Regex("""(?<![\p{L}\p{N}_@.])@[\p{L}\p{N}_.]{2,32}""")

    private class Link(val start: Int, val end: Int, val type: SpanType, val data: String)

    fun parse(raw: String): ParsedText {
        val text = if (raw.length > MAX_LENGTH) raw.take(MAX_LENGTH) else raw
        val sb = StringBuilder()
        val spans = ArrayList<TextSpan>()
        parseInto(text, sb, spans, depth = 0)
        return ParsedText(sb.toString(), spans.sortedWith(compareBy({ it.start }, { -it.end })))
    }

    /** Text without markers (for chat-list previews, notifications, copy). */
    fun plainText(raw: String): String = parse(raw).plain

    private fun parseInto(s: String, sb: StringBuilder, spans: MutableList<TextSpan>, depth: Int) {
        val links = findLinks(s)
        var linkIndex = 0
        var i = 0
        while (i < s.length) {
            while (linkIndex < links.size && links[linkIndex].end <= i) linkIndex++
            val link = links.getOrNull(linkIndex)
            if (link != null && link.start == i) {
                val start = sb.length
                sb.append(s, link.start, link.end)
                spans.add(TextSpan(start, sb.length, link.type, link.data))
                i = link.end
                continue
            }
            // Never start a style marker inside an auto-detected link (e.g. "__" in a URL).
            val insideLink = link != null && i > link.start && i < link.end
            val delimiter = if (!insideLink && depth < MAX_DEPTH) delimiters.firstOrNull { s.startsWith(it.token, i) } else null
            if (delimiter != null) {
                val contentStart = i + delimiter.token.length
                val close = s.indexOf(delimiter.token, contentStart)
                if (close > contentStart && s.substring(contentStart, close).isNotBlank() && !crossesLinkBoundary(links, contentStart, close)) {
                    val content = s.substring(contentStart, close)
                    val start = sb.length
                    if (delimiter.type == SpanType.CODE) sb.append(content) else parseInto(content, sb, spans, depth + 1)
                    spans.add(TextSpan(start, sb.length, delimiter.type))
                    i = close + delimiter.token.length
                    continue
                }
            }
            sb.append(s[i])
            i++
        }
    }

    /** A style pair whose closing marker sits inside a link would cut the link in two: leave such markers literal. */
    private fun crossesLinkBoundary(links: List<Link>, from: Int, to: Int): Boolean =
        links.any { it.start in (from + 1) until to && it.end > to } || links.any { it.start < from && it.end > from && it.end <= to }

    private fun findLinks(s: String): List<Link> {
        val found = ArrayList<Link>()
        fun overlaps(start: Int, end: Int) = found.any { start < it.end && end > it.start }
        fun add(start: Int, end: Int, type: SpanType, data: String) {
            if (end > start && !overlaps(start, end)) found.add(Link(start, end, type, data))
        }
        // Priority order: a URL containing an '@' stays a URL, an e-mail is not also a mention, and so on.
        for (m in urlRegex.findAll(s)) {
            val trimmed = trimTrailingPunctuation(m.value)
            if (trimmed.length > 4) {
                val target = if (trimmed.startsWith("www.", ignoreCase = true)) "https://$trimmed" else trimmed
                add(m.range.first, m.range.first + trimmed.length, SpanType.URL, target)
            }
        }
        for (m in emailRegex.findAll(s)) add(m.range.first, m.range.last + 1, SpanType.EMAIL, "mailto:${m.value}")
        for (m in phoneRegex.findAll(s)) {
            val digits = normalizeDigits(m.value).filter { it.isDigit() }
            val international = m.value.trimStart().startsWith("+")
            if (digits.length in 10..15 || (international && digits.length in 7..15)) {
                add(m.range.first, m.range.last + 1, SpanType.PHONE, "tel:" + (if (international) "+" else "") + digits)
            }
        }
        for (m in hashtagRegex.findAll(s)) add(m.range.first, m.range.last + 1, SpanType.HASHTAG, m.value.removePrefix("#"))
        for (m in mentionRegex.findAll(s)) add(m.range.first, m.range.last + 1, SpanType.MENTION, m.value.removePrefix("@").trimEnd('.'))
        return found.sortedBy { it.start }
    }

    private fun trimTrailingPunctuation(url: String): String {
        var end = url.length
        while (end > 0) {
            // A closing style marker typed right after a link ("**see https://a.com**") is not part of the link.
            val closing = listOf("**", "__", "~~", "||").firstOrNull { url.substring(0, end).endsWith(it) }
            if (closing != null) {
                end -= closing.length
                continue
            }
            if (url[end - 1] == '`') {
                end--
                continue
            }
            if (url[end - 1] !in ".,;:!?)]}\u061F\u060C") break
            // keep a closing ')' that balances an opening one inside the URL (e.g. wikipedia links)
            if (url[end - 1] == ')' && url.count { it == '(' } >= url.take(end).count { it == ')' }) break
            end--
        }
        return url.substring(0, end)
    }

    /** Persian / Arabic-Indic digits -> ASCII, so a phone number typed with a Persian keyboard still dials. */
    fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    else -> ch
                },
            )
        }
        return sb.toString()
    }
}
