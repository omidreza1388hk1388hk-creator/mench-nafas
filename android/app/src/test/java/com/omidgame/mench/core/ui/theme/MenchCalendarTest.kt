package com.omidgame.mench.core.ui.theme

import com.google.common.truth.Truth.assertThat
import com.omidgame.mench.feature.appearance.domain.CalendarStyle
import com.omidgame.mench.feature.appearance.domain.DigitStyle
import org.junit.Test

class MenchCalendarTest {
    @Test
    fun `default is jalali with the locale digits`() {
        assertThat(MenchCalendar.localeTag(CalendarStyle.JALALI, DigitStyle.AUTO))
            .isEqualTo("fa_IR@calendar=persian")
    }

    @Test
    fun `gregorian switches the calendar keyword`() {
        assertThat(MenchCalendar.localeTag(CalendarStyle.GREGORIAN, DigitStyle.AUTO))
            .isEqualTo("fa_IR@calendar=gregorian")
    }

    @Test
    fun `digit style adds the numbering system`() {
        assertThat(MenchCalendar.localeTag(CalendarStyle.JALALI, DigitStyle.LATIN))
            .isEqualTo("fa_IR@calendar=persian;numbers=latn")
        assertThat(MenchCalendar.localeTag(CalendarStyle.GREGORIAN, DigitStyle.PERSIAN))
            .isEqualTo("fa_IR@calendar=gregorian;numbers=arabext")
    }

    @Test
    fun `wire values round trip and fall back to jalali`() {
        assertThat(CalendarStyle.fromWireValue("gregorian")).isEqualTo(CalendarStyle.GREGORIAN)
        assertThat(CalendarStyle.fromWireValue("nonsense")).isEqualTo(CalendarStyle.JALALI)
        assertThat(CalendarStyle.fromWireValue(null)).isEqualTo(CalendarStyle.JALALI)
    }
}
