package com.omidgame.mench.feature.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterCountdownTest {
    @Test
    fun splitsIntoDaysHoursAndMinutes() {
        val now = 1_000_000L
        val unlock = now + (3L * 24 * 60 + 5L * 60 + 7L) * 60_000L
        assertEquals(Triple(3L, 5L, 7L), splitCountdown(unlock, now))
    }

    @Test
    fun pastUnlockTimeIsAllZero() {
        assertEquals(Triple(0L, 0L, 0L), splitCountdown(1_000L, 5_000_000L))
    }
}
