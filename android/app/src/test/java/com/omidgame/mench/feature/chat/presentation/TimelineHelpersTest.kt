package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Memory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineHelpersTest {
    private fun memory(date: String) = Memory("id-$date", "t", null, date, null, true)

    @Test
    fun matchesSameMonthAndDayInEarlierYearsOnly() {
        val today = LocalDate.of(2026, 10, 4)
        val result = onThisDay(
            listOf(memory("2024-10-04"), memory("2025-10-04"), memory("2026-10-04"), memory("2025-10-05"), memory("bad")),
            today,
        )
        assertEquals(listOf(2, 1), result.map { it.yearsAgo })
    }

    @Test
    fun leapDayShowsOnFeb28InNonLeapYears() {
        val result = onThisDay(listOf(memory("2024-02-29")), LocalDate.of(2026, 2, 28))
        assertEquals(1, result.size)
        assertEquals(2, result[0].yearsAgo)
    }

    @Test
    fun milestones() {
        assertTrue(isMilestone(100)); assertTrue(isMilestone(365)); assertTrue(isMilestone(1095))
        assertFalse(isMilestone(0)); assertFalse(isMilestone(101)); assertFalse(isMilestone(1001))
    }

    @Test
    fun nextMilestone() {
        assertEquals(100L to 40L, nextMilestone(60))
        assertEquals(365L to 1L, nextMilestone(364))
        assertEquals(1095L to 95L, nextMilestone(1000))
    }
}
