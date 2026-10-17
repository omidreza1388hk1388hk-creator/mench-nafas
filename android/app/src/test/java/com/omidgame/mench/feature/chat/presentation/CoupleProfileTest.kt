package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Counter
import com.omidgame.mench.feature.chat.domain.Question
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoupleProfileTest {
    private val today = LocalDate.of(2026, 10, 4)
    private fun counter(kind: String, tally: Int, id: String = "$kind$tally") = Counter(id, "t", "💗", kind, null, tally, true)
    private fun build(together: String? = null, meet: String? = null, counters: List<Counter> = emptyList(), q: Question? = null) =
        buildCoupleProfile(today, together, meet, emptyList(), emptyList(), counters, q)

    @Test
    fun daysTogetherAndMilestones() {
        val p = build(together = "2026-06-26") // 100 days
        assertEquals(100L, p.daysTogether)
        assertTrue(p.milestoneToday)
        assertEquals(200L to 100L, p.nextMilestone)
        assertFalse(build(together = "2026-06-27").milestoneToday)
    }

    @Test
    fun missingBadOrFutureTogetherDateMeansNoCount() {
        assertNull(build().daysTogether)
        assertNull(build(together = "nope").daysTogether)
        assertNull(build(together = "2027-01-01").daysTogether)
        assertNull(build(together = "2027-01-01").nextMilestone)
    }

    @Test
    fun nextMeetIsOnlyShownWhenTodayOrFuture() {
        assertEquals(5L, build(meet = "2026-10-09").daysToNextMeet)
        assertEquals(0L, build(meet = "2026-10-04").daysToNextMeet)
        assertNull(build(meet = "2026-10-03").daysToNextMeet)
    }

    @Test
    fun featuredCountersPutBigTalliesFirstAndCapAtThree() {
        val p = build(counters = listOf(counter("date", 0), counter("tally", 2), counter("tally", 9), counter("tally", 5)))
        assertEquals(listOf(9, 5, 2), p.featuredCounters.map { it.tallyCount })
    }

    @Test
    fun streakComesFromTheQuestion() {
        val q = Question("2026-10-04", "q", null, false, null, 4, 9)
        assertEquals(4, build(q = q).streak)
        assertEquals(9, build(q = q).bestStreak)
        assertEquals(0, build().streak)
    }
}
