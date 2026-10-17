package com.omidgame.mench.core.ui.theme

import android.icu.util.ULocale
import com.omidgame.mench.feature.appearance.domain.CalendarStyle
import com.omidgame.mench.feature.appearance.domain.DigitStyle

/**
 * Single source of truth for how dates are formatted (Phase 4A): every formatter that used to
 * hard-code the Persian calendar now asks [locale]. MainActivity pushes the user's choice in
 * through [configure]; screens pick the change up the next time they compose.
 */
object MenchCalendar {
    @Volatile private var calendar: CalendarStyle = CalendarStyle.JALALI

    @Volatile private var digits: DigitStyle = DigitStyle.AUTO

    fun configure(calendar: CalendarStyle, digits: DigitStyle) {
        this.calendar = calendar
        this.digits = digits
    }

    fun locale(): ULocale = ULocale(localeTag(calendar, digits))

    /** Pure (no ICU) so it can be unit-tested on the JVM. */
    internal fun localeTag(calendar: CalendarStyle, digits: DigitStyle): String {
        val cal = if (calendar == CalendarStyle.GREGORIAN) "gregorian" else "persian"
        val numbers = when (digits) {
            DigitStyle.PERSIAN -> ";numbers=arabext"
            DigitStyle.LATIN -> ";numbers=latn"
            DigitStyle.AUTO -> ""
        }
        return "fa_IR@calendar=$cal$numbers"
    }
}
