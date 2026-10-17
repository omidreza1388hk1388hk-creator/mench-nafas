package com.omidgame.mench.feature.chat.presentation

import java.time.LocalDate
import java.time.Month

/**
 * Phase 3B — seasonal ambience. On a few special days the chat plays one gentle full-screen effect the first time
 * it is opened that day. Dates use the Gregorian calendar (Nowruz and Yalda are fixed to the equinox/solstice,
 * which fall on these dates in the years that matter here).
 */
internal fun seasonalEffectFor(date: LocalDate): MessageEffect? = lunarEffectFor(date) ?: gregorianEffectFor(date)

/**
 * Islamic-calendar occasions, from the Umm al-Qura calendar built into Java: Eid al-Fitr (1 Shawwal) and Eid al-Adha
 * (10 Dhu al-Hijjah) get confetti, Eid al-Ghadir (18 Dhu al-Hijjah) hearts, and the night of Mid-Sha'ban (15 Sha'ban) lanterns.
 * Local moon sighting can differ from the calendar by a day, so the effect may land a day early or late.
 */
internal fun lunarEffectFor(date: LocalDate): MessageEffect? {
    val hijri = runCatching { java.time.chrono.HijrahDate.from(date) }.getOrNull() ?: return null
    val month = hijri.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
    val day = hijri.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
    return when {
        month == 10 && day == 1 -> MessageEffect.CONFETTI
        month == 12 && day == 10 -> MessageEffect.CONFETTI
        month == 12 && day == 18 -> MessageEffect.HEARTS
        month == 8 && day == 15 -> MessageEffect.LANTERNS
        else -> null
    }
}

internal fun gregorianEffectFor(date: LocalDate): MessageEffect? = when {
    date.month == Month.FEBRUARY && date.dayOfMonth == 14 -> MessageEffect.HEARTS // Valentine's Day
    date.month == Month.MARCH && date.dayOfMonth in 20..22 -> MessageEffect.CONFETTI // Nowruz
    date.month == Month.DECEMBER && date.dayOfMonth in 20..21 -> MessageEffect.NIGHT // Yalda, the longest night
    date.month == Month.JANUARY && date.dayOfMonth == 1 -> MessageEffect.CONFETTI // New Year
    else -> null
}
