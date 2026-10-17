package com.omidgame.mench.feature.chat.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YearMessageStatsTest {
    private val utc = ZoneId.of("UTC")
    private fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 0).atZone(utc).toInstant().toEpochMilli()
    private fun row(body: String, own: Boolean, date: String = "2026-03-01", hour: Int = 12) = YearMessageRow(body, own, at(date, hour))

    @Test
    fun emptyInputGivesEmptyStats() {
        assertEquals(YearMessageStats.EMPTY, computeYearMessageStats(emptyList(), utc))
        assertNull(YearMessageStats.EMPTY.busiestHour)
    }

    @Test
    fun countsSentReceivedAndFindsBusiestHourAndDay() {
        val rows = listOf(
            row("hi", true, "2026-03-01", 23), row("hello", false, "2026-03-01", 23), row("yo", true, "2026-03-01", 22),
            row("later", false, "2026-04-02", 9),
        )
        val s = computeYearMessageStats(rows, utc)
        assertEquals(2, s.sent); assertEquals(2, s.received); assertEquals(4, s.total)
        assertEquals(23, s.busiestHour)
        assertEquals(LocalDate.parse("2026-03-01"), s.busiestDay)
        assertEquals(3, s.busiestDayCount)
    }

    @Test
    fun findsTheTopEmoji() {
        val s = computeYearMessageStats(listOf(row("\u2764\uFE0F love \uD83D\uDE0D\uD83D\uDE0D", true), row("\uD83D\uDE0D", false)), utc)
        assertEquals("\uD83D\uDE0D", s.topEmoji)
        assertEquals(3, s.topEmojiCount)
    }

    @Test
    fun topWordsSkipFillerAndShortWordsAndWorkInPersian() {
        val rows = listOf(row("the coffee and the coffee tea", true), row("coffee ok", false), row("دوستت دارم دوستت", true), row("دوستت", false))
        val s = computeYearMessageStats(rows, utc)
        assertEquals("coffee", s.topWords[0])
        assertTrue("دوستت" in s.topWords)
        assertTrue("the" !in s.topWords && "ok" !in s.topWords)
        assertTrue(s.topWords.size <= 3)
    }
}
