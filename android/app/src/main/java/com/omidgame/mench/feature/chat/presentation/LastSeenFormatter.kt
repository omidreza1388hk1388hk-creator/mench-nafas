package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.core.ui.theme.MenchCalendar
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import java.util.Date

/**
 * Formats last-seen times in the Persian (Jalali) calendar with Persian
 * digits via ICU (available on API 24+; this app's minSdk is 26).
 * Returns "today"/"yesterday" as a flag so the caller can pick the localized
 * word from string resources rather than hardcoding text here.
 */
object LastSeenFormatter {
    enum class Day { TODAY, YESTERDAY, OTHER }

    data class Result(val day: Day, val time: String, val date: String)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun format(lastSeenEpochMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): Result {
        val locale = MenchCalendar.locale()
        val time = SimpleDateFormat("HH:mm", locale).format(Date(lastSeenEpochMillis))
        val date = SimpleDateFormat("yyyy/MM/dd", locale).format(Date(lastSeenEpochMillis))
        val dayKey = SimpleDateFormat("yyyyMMdd", ULocale.ROOT)
        val seenKey = dayKey.format(Date(lastSeenEpochMillis))
        val day = when (seenKey) {
            dayKey.format(Date(nowEpochMillis)) -> Day.TODAY
            dayKey.format(Date(nowEpochMillis - DAY_MS)) -> Day.YESTERDAY
            else -> Day.OTHER
        }
        return Result(day, time, date)
    }
}

/** Jalali day headers for the message list ("۱۲ مهر", with the year only when it isn't the current one). */
object ChatDateFormatter {
    data class Header(val day: LastSeenFormatter.Day, val text: String)

    private val locale get() = MenchCalendar.locale()

    /** "۱۴:۰۵" — Persian digits, 24h. Shown inside message bubbles. */
    fun time(epochMillis: Long): String = SimpleDateFormat("HH:mm", locale).format(Date(epochMillis))

    fun dayKey(epochMillis: Long): String = SimpleDateFormat("yyyyMMdd", ULocale.ROOT).format(Date(epochMillis))

    fun header(epochMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): Header {
        val day = LastSeenFormatter.format(epochMillis, nowEpochMillis).day
        val year = SimpleDateFormat("yyyy", locale)
        val sameYear = year.format(Date(epochMillis)) == year.format(Date(nowEpochMillis))
        val text = SimpleDateFormat(if (sameYear) "d MMMM" else "d MMMM yyyy", locale).format(Date(epochMillis))
        return Header(day, text)
    }
}
