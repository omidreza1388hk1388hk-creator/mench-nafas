package com.omidgame.mench.feature.chat.presentation

/**
 * Detects "emoji-only" messages so the bubble can render them big and bubble-less
 * (1-3 emoji), like Telegram / iMessage. Pure Kotlin, no Android types, so it is
 * covered by a plain JVM unit test.
 *
 * Works on code points rather than java.text.BreakIterator because the platform
 * BreakIterator splits ZWJ / flag / keycap sequences differently across Android versions.
 */
object EmojiText {
    /** Emoji-only messages longer than this render as normal text. */
    const val MAX_BIG_EMOJI = 3

    private const val ZWJ = 0x200D
    private const val VS16 = 0xFE0F
    private const val KEYCAP = 0x20E3

    /**
     * Number of emoji (grapheme clusters) when [text] contains ONLY emoji and whitespace,
     * 0 when it has any other character, is blank, or exceeds [MAX_BIG_EMOJI].
     */
    fun bigEmojiCount(text: String?): Int {
        val count = emojiOnlyCount(text)
        return if (count in 1..MAX_BIG_EMOJI) count else 0
    }

    /** Count of emoji clusters if the text is emoji + whitespace only; 0 otherwise (no upper bound). */
    fun emojiOnlyCount(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val cps = text.codePoints().toArray()
        var clusters = 0
        var i = 0
        var joinNext = false // previous code point was a ZWJ: the next emoji extends the current cluster
        while (i < cps.size) {
            val cp = cps[i]
            when {
                Character.isWhitespace(cp) -> joinNext = false
                cp == ZWJ -> {
                    if (clusters == 0) return 0
                    joinNext = true
                }
                cp == VS16 || cp == KEYCAP || isSkinTone(cp) || isTag(cp) -> {
                    if (clusters == 0) return 0
                }
                isRegionalIndicator(cp) -> {
                    // A flag is two regional indicators: the second joins the first.
                    val prevIsLoneRegional = i > 0 && isRegionalIndicator(cps[i - 1]) && !joinNext &&
                        regionalRunLengthBefore(cps, i) % 2 == 1
                    if (!prevIsLoneRegional && !joinNext) clusters++
                    joinNext = false
                }
                isKeycapBase(cp) -> {
                    // Digits / # / * only count as emoji when followed by VS16 and/or the keycap mark.
                    val next = cps.getOrNull(i + 1)
                    val next2 = cps.getOrNull(i + 2)
                    val isKeycap = next == KEYCAP || (next == VS16 && next2 == KEYCAP)
                    if (!isKeycap) return 0
                    clusters++
                    joinNext = false
                }
                isPictographic(cp) -> {
                    if (!joinNext) clusters++
                    joinNext = false
                }
                else -> return 0
            }
            i++
        }
        return clusters
    }

    private fun regionalRunLengthBefore(cps: IntArray, index: Int): Int {
        var n = 0
        var j = index - 1
        while (j >= 0 && isRegionalIndicator(cps[j])) {
            n++
            j--
        }
        return n
    }

    private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF
    private fun isSkinTone(cp: Int) = cp in 0x1F3FB..0x1F3FF
    private fun isTag(cp: Int) = cp in 0xE0020..0xE007F
    private fun isKeycapBase(cp: Int) = cp == '#'.code || cp == '*'.code || cp in '0'.code..'9'.code

    private fun isPictographic(cp: Int): Boolean =
        cp in 0x1F300..0x1FAFF ||   // symbols & pictographs, emoticons, transport, supplemental, extended-A
            cp in 0x1F000..0x1F2FF || // mahjong, cards, enclosed alphanumerics supplement
            cp in 0x2600..0x27BF ||   // misc symbols + dingbats (hearts, stars, scissors, check marks)
            cp in 0x2300..0x23FF ||   // misc technical (watch, hourglass, media controls)
            cp in 0x2B00..0x2BFF ||   // arrows / stars / squares (star, large circles)
            cp in 0x2190..0x21FF ||   // arrows (some are emoji)
            cp in 0x2900..0x297F ||   // supplemental arrows-B (curved arrows)
            cp in 0x25AA..0x25FE ||   // geometric shapes that have emoji presentation
            cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 ||
            cp == 0x2139 || cp == 0x24C2 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299
}
