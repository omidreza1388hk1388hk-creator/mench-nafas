package com.omidgame.mench.feature.chat.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Phase 3A — what your own chat says about the year. Computed on the phone from the messages it already holds. */
data class YearMessageStats(
    val sent: Int,
    val received: Int,
    /** 0..23 — the hour of day with the most messages; null when there were none. */
    val busiestHour: Int?,
    val busiestDay: LocalDate?,
    val busiestDayCount: Int,
    val topEmoji: String?,
    val topEmojiCount: Int,
    /** Up to three most used words (3+ letters, common filler words removed), most used first. */
    val topWords: List<String>,
) {
    val total: Int get() = sent + received

    companion object {
        val EMPTY = YearMessageStats(0, 0, null, null, 0, null, 0, emptyList())
    }
}

/** One text message reduced to what the statistics need. */
data class YearMessageRow(val body: String, val isOwn: Boolean, val createdAtEpochMillis: Long)

private val stopWords = setOf(
    "the", "and", "you", "for", "that", "this", "with", "have", "are", "was", "not", "but", "your", "can", "just", "what", "how", "all",
    "که", "این", "من", "تو", "رو", "از", "به", "با", "را", "هم", "برای", "اون", "یه", "هست", "ولی", "اما", "هم", "یک", "اگه", "چی", "نه", "آره",
)

/** True for the code points that draw as emoji/pictographs (the common blocks); modifiers and joiners are excluded. */
internal fun isEmojiCodePoint(cp: Int): Boolean =
    cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x1F000..0x1F2FF || cp == 0x2764 || cp == 0x2B50

fun computeYearMessageStats(rows: List<YearMessageRow>, zone: ZoneId = ZoneId.systemDefault()): YearMessageStats {
    if (rows.isEmpty()) return YearMessageStats.EMPTY
    val hours = IntArray(24)
    val days = HashMap<LocalDate, Int>()
    val emoji = HashMap<String, Int>()
    val words = HashMap<String, Int>()
    for (row in rows) {
        val at = Instant.ofEpochMilli(row.createdAtEpochMillis).atZone(zone)
        hours[at.hour]++
        days.merge(at.toLocalDate(), 1, Int::plus)
        var i = 0
        val text = row.body
        val word = StringBuilder()
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (isEmojiCodePoint(cp)) {
                emoji.merge(String(Character.toChars(cp)), 1, Int::plus)
            }
            if (Character.isLetter(cp)) {
                word.appendCodePoint(Character.toLowerCase(cp))
            } else {
                flushWord(word, words)
            }
        }
        flushWord(word, words)
    }
    val busiestHour = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    val busiestDay = days.maxWithOrNull(compareBy<Map.Entry<LocalDate, Int>> { it.value }.thenByDescending { it.key })
    val topEmoji = emoji.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    return YearMessageStats(
        sent = rows.count { it.isOwn },
        received = rows.count { !it.isOwn },
        busiestHour = busiestHour,
        busiestDay = busiestDay?.key,
        busiestDayCount = busiestDay?.value ?: 0,
        topEmoji = topEmoji?.key,
        topEmojiCount = topEmoji?.value ?: 0,
        topWords = words.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(3).map { it.key },
    )
}

private fun flushWord(word: StringBuilder, into: MutableMap<String, Int>) {
    if (word.isNotEmpty()) {
        val w = word.toString()
        if (w.codePointCount(0, w.length) >= 3 && w !in stopWords) into.merge(w, 1, Int::plus)
        word.setLength(0)
    }
}
