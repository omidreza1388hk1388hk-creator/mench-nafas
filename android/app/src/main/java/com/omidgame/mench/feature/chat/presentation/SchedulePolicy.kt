package com.omidgame.mench.feature.chat.presentation

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A one-tap suggestion in the "schedule" sheet. [labelKey] picks the string; [atEpochMillis] is the resolved time. */
data class ScheduleOption(val labelKey: Key, val atEpochMillis: Long) {
    enum class Key { IN_ONE_HOUR, TONIGHT, TOMORROW_MORNING, TOMORROW_EVENING }
}

/**
 * Phase 2A — "send later" rules. The server enforces the same window (10 s .. 365 days); the app
 * asks for a larger minimum margin so a message picked "right now" is never rejected in transit.
 * Pure Kotlin (java.time), unit-tested on the JVM.
 */
object SchedulePolicy {
    const val MIN_LEAD_MS = 60_000L
    const val MAX_LEAD_MS = 365L * 24 * 60 * 60 * 1000

    fun isAcceptable(atEpochMillis: Long, nowEpochMillis: Long): Boolean =
        atEpochMillis >= nowEpochMillis + MIN_LEAD_MS && atEpochMillis <= nowEpochMillis + MAX_LEAD_MS

    /** Combines a picked calendar date and wall-clock time in [zone] into an instant (DST-safe via java.time). */
    fun combine(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    /**
     * Quick choices. "Tonight 21:00" is dropped once it is within the minimum margin of now (then
     * the evening choice of tomorrow remains); "Tomorrow" times are always on the next calendar day.
     */
    fun quickOptions(nowEpochMillis: Long, zone: ZoneId): List<ScheduleOption> {
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val out = ArrayList<ScheduleOption>()
        out.add(ScheduleOption(ScheduleOption.Key.IN_ONE_HOUR, nowEpochMillis + 60 * 60 * 1000L))
        val tonight = combine(today, LocalTime.of(21, 0), zone)
        if (isAcceptable(tonight, nowEpochMillis)) out.add(ScheduleOption(ScheduleOption.Key.TONIGHT, tonight))
        out.add(ScheduleOption(ScheduleOption.Key.TOMORROW_MORNING, combine(tomorrow, LocalTime.of(9, 0), zone)))
        out.add(ScheduleOption(ScheduleOption.Key.TOMORROW_EVENING, combine(tomorrow, LocalTime.of(20, 0), zone)))
        return out
    }
}
