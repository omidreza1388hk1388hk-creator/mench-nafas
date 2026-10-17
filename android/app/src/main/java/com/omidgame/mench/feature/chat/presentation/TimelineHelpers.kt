package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Memory
import java.time.LocalDate

/** Phase 3A — "on this day": pure helpers for the love timeline and counter milestones. */

/** A memory that happened on today's month and day in an earlier year. */
data class OnThisDay(val memory: Memory, val yearsAgo: Int)

/** Memories dated on the same month/day as [today] in an earlier year, oldest first. Feb 29 memories show on Feb 28 in non-leap years. */
internal fun onThisDay(memories: List<Memory>, today: LocalDate): List<OnThisDay> =
    memories.mapNotNull { memory ->
        val date = runCatching { LocalDate.parse(memory.memoryDate) }.getOrNull() ?: return@mapNotNull null
        val years = today.year - date.year
        if (years <= 0) return@mapNotNull null
        val sameDay = (date.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth) ||
            (date.monthValue == 2 && date.dayOfMonth == 29 && !today.isLeapYear && today.monthValue == 2 && today.dayOfMonth == 28)
        if (sameDay) OnThisDay(memory, years) else null
    }.sortedByDescending { it.yearsAgo }

/** Milestones worth celebrating, in days. Beyond 1000, every 365 days (each anniversary). */
private val fixedMilestones = listOf(50, 100, 200, 300, 365, 500, 730, 1000)

/** True when [days] (days since the anchor) is itself a milestone. */
internal fun isMilestone(days: Long): Boolean =
    days > 0 && (days in fixedMilestones.map { it.toLong() } || (days > 1000 && days % 365 == 0L))

/** The next milestone strictly after [days], and how many days away it is. */
internal fun nextMilestone(days: Long): Pair<Long, Long> {
    val next = fixedMilestones.map { it.toLong() }.firstOrNull { it > days }
        ?: ((days / 365) + 1) * 365
    return next to (next - days)
}

/** The distinct years that have at least one memory, newest first — the choices of the timeline's year filter. */
internal fun memoryYears(memories: List<Memory>): List<Int> =
    memories.mapNotNull { runCatching { LocalDate.parse(it.memoryDate).year }.getOrNull() }.distinct().sortedDescending()

/** Memories of [year] (or all of them when [year] is null), keeping their order. */
internal fun memoriesOfYear(memories: List<Memory>, year: Int?): List<Memory> =
    if (year == null) memories else memories.filter { runCatching { LocalDate.parse(it.memoryDate).year }.getOrNull() == year }

/**
 * "Memory of the week": one memory chosen deterministically from the ISO week of [today], so it stays the same all week
 * and changes on Monday. Null when there are no memories.
 */
internal fun memoryOfTheWeek(memories: List<Memory>, today: LocalDate): Memory? {
    if (memories.isEmpty()) return null
    val week = today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val weekYear = today.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)
    return memories[Math.floorMod(weekYear * 53 + week, memories.size)]
}
