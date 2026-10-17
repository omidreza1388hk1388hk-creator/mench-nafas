package com.omidgame.mench.feature.chat.presentation

import java.time.LocalDate
import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalBatchTest {
    @Test
    fun syncRuleCountsHoldingAndTheSixSecondsAfterRelease() {
        assertTrue(isSyncedAt(holding = true, lastReleaseAt = 0, nowMillis = 99_999))
        assertTrue(isSyncedAt(holding = false, lastReleaseAt = 10_000, nowMillis = 14_000))
        assertTrue(isSyncedAt(holding = false, lastReleaseAt = 10_000, nowMillis = 16_000))
        assertFalse(isSyncedAt(holding = false, lastReleaseAt = 10_000, nowMillis = 16_001))
        assertFalse(isSyncedAt(holding = false, lastReleaseAt = 10_000, nowMillis = 9_000))
        assertFalse(isSyncedAt(holding = false, lastReleaseAt = Long.MIN_VALUE / 2, nowMillis = 1_000))
    }

    @Test
    fun frameBudgetUsesHysteresis() {
        assertFalse(shouldDegrade(16.7, currentlyDegraded = false))
        assertTrue(shouldDegrade(30.0, currentlyDegraded = false))
        assertTrue(shouldDegrade(20.0, currentlyDegraded = true))   // still above the recovery line
        assertFalse(shouldDegrade(16.0, currentlyDegraded = true))  // recovered
    }

    @Test
    fun birthdayCountdown() {
        val today = LocalDate.of(2026, 10, 4)
        assertEquals(0L, daysUntilBirthday(MonthDay.of(10, 4), today))
        assertEquals(1L, daysUntilBirthday(MonthDay.of(10, 5), today))
        assertEquals(365L - 1L, daysUntilBirthday(MonthDay.of(10, 3), today)) // next year, not a leap year
        assertEquals(LocalDate.of(2027, 2, 28), today.plusDays(daysUntilBirthday(MonthDay.of(2, 29), today)))
    }

    @Test
    fun headerFadesButNeverVanishes() {
        assertEquals(1f, headerAlpha(0f), 0f)
        assertEquals(0.25f, headerAlpha(5000f), 0f)
        assertTrue(headerAlpha(300f) in 0.25f..1f)
    }
}
