package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Memory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineYearTest {
    private fun memory(date: String) = Memory("id-$date", "t-$date", null, date, null, true)
    private val all = listOf(memory("2024-05-01"), memory("2025-03-03"), memory("2025-12-12"), memory("2026-01-01"))

    @Test
    fun yearsAreDistinctAndNewestFirst() = assertEquals(listOf(2026, 2025, 2024), memoryYears(all))

    @Test
    fun filterKeepsOnlyThatYearOrEverything() {
        assertEquals(2, memoriesOfYear(all, 2025).size)
        assertEquals(all, memoriesOfYear(all, null))
    }

    @Test
    fun memoryOfTheWeekIsStableWithinAWeekAndChangesNextWeek() {
        val monday = LocalDate.of(2026, 10, 5)
        assertEquals(memoryOfTheWeek(all, monday), memoryOfTheWeek(all, monday.plusDays(6)))
        assertEquals(null, memoryOfTheWeek(emptyList(), monday))
        val picks = (0 until 8).map { memoryOfTheWeek(all, monday.plusWeeks(it.toLong())) }.toSet()
        assert(picks.size > 1)
    }

    @Test
    fun badDatesAreIgnored() {
        assertEquals(emptyList<Int>(), memoryYears(listOf(memory("nope"))))
        assertNull(memoryOfTheWeek(emptyList(), LocalDate.now()))
    }
}
