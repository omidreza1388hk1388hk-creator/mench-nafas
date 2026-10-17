package com.omidgame.mench.feature.chat.presentation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CounterDaysTest {
    private val today = LocalDate.of(2026, 10, 4)

    @Test
    fun pastDateCountsDaysSince() = assertEquals(10L, dateCounterDays("2026-09-24", today))

    @Test
    fun futureDateIsNegative() = assertEquals(-5L, dateCounterDays("2026-10-09", today))

    @Test
    fun sameDayIsZero() = assertEquals(0L, dateCounterDays("2026-10-04", today))

    @Test
    fun badOrMissingDateIsNull() {
        assertNull(dateCounterDays("not-a-date", today))
        assertNull(dateCounterDays(null, today))
    }
}
