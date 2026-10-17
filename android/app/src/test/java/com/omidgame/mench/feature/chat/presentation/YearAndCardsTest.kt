package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Counter
import com.omidgame.mench.feature.chat.domain.Letter
import com.omidgame.mench.feature.chat.domain.Memory
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YearAndCardsTest {
    private val today = LocalDate.of(2026, 10, 4)
    private fun memory(date: String, title: String = "t") = Memory("m-$date", title, null, date, null, true)
    private fun letter(isMine: Boolean, year: Int) = Letter(
        id = "l$year$isMine", title = "x", body = null, unlockAtEpochMillis = null, openedAtEpochMillis = null,
        createdAtEpochMillis = LocalDate.of(year, 6, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        isMine = isMine, canOpen = true,
    )
    private fun counter(kind: String, tally: Int) = Counter("c$kind$tally", "t", "💗", kind, null, tally, true)

    @Test
    fun countsOnlyThisYearsMemoriesAndPicksTheEarliest() {
        val s = computeYearStats(today, "2026-09-24", listOf(memory("2026-08-01", "later"), memory("2026-02-01", "first"), memory("2025-12-31")), emptyList(), emptyList())
        assertEquals(2, s.memoriesThisYear)
        assertEquals("first", s.firstMemoryTitle)
        assertEquals(10L, s.daysTogether)
    }

    @Test
    fun noDateMeansNoDaysTogether() {
        assertNull(computeYearStats(today, null, emptyList(), emptyList(), emptyList()).daysTogether)
        assertNull(computeYearStats(today, "garbage", emptyList(), emptyList(), emptyList()).daysTogether)
    }

    @Test
    fun splitsLettersAndSumsOnlyTallies() {
        val s = computeYearStats(today, null, emptyList(), listOf(letter(true, 2026), letter(false, 2026), letter(false, 2025)), listOf(counter("tally", 7), counter("tally", 3), counter("date", 99)))
        assertEquals(1, s.lettersWritten)
        assertEquals(1, s.lettersReceived)
        assertEquals(3, s.counterCount)
        assertEquals(10, s.tallyTotal)
    }

    @Test
    fun nextCardNeverRepeatsWhenThereIsChoice() {
        repeat(50) { assertNotEquals(2, nextCardIndex(5, 2, Random(it))) }
        assertEquals(0, nextCardIndex(1, 0))
    }
}
