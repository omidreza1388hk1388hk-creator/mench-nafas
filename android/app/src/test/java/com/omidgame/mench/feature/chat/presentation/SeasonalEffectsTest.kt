package com.omidgame.mench.feature.chat.presentation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeasonalEffectsTest {
    @Test
    fun specialDays() {
        assertEquals(MessageEffect.HEARTS, seasonalEffectFor(LocalDate.of(2027, 2, 14)))
        assertEquals(MessageEffect.CONFETTI, seasonalEffectFor(LocalDate.of(2027, 3, 21)))
        assertEquals(MessageEffect.NIGHT, seasonalEffectFor(LocalDate.of(2026, 12, 21)))
        assertEquals(MessageEffect.CONFETTI, seasonalEffectFor(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun islamicOccasions() {
        fun day(year: Int, month: Int, d: Int) = LocalDate.from(java.time.chrono.HijrahDate.of(year, month, d))
        assertEquals(MessageEffect.CONFETTI, lunarEffectFor(day(1448, 10, 1)))
        assertEquals(MessageEffect.CONFETTI, lunarEffectFor(day(1447, 12, 10)))
        assertEquals(MessageEffect.HEARTS, lunarEffectFor(day(1447, 12, 18)))
        assertEquals(MessageEffect.LANTERNS, lunarEffectFor(day(1448, 8, 15)))
        assertNull(lunarEffectFor(day(1448, 5, 5)))
    }

    @Test
    fun ordinaryDaysHaveNone() {
        assertNull(seasonalEffectFor(LocalDate.of(2026, 10, 4)))
        assertNull(seasonalEffectFor(LocalDate.of(2027, 2, 15)))
    }
}
