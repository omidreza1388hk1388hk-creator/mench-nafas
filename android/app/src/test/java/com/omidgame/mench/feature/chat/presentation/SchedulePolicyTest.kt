package com.omidgame.mench.feature.chat.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SchedulePolicyTest {
    private val tehran = ZoneId.of("Asia/Tehran")
    private val now = ZonedDateTime.of(2026, 10, 5, 14, 30, 0, 0, tehran).toInstant().toEpochMilli()

    @Test
    fun `acceptable window is one minute to one year`() {
        assertThat(SchedulePolicy.isAcceptable(now + 30_000, now)).isFalse()
        assertThat(SchedulePolicy.isAcceptable(now + 60_000, now)).isTrue()
        assertThat(SchedulePolicy.isAcceptable(now - 1, now)).isFalse()
        assertThat(SchedulePolicy.isAcceptable(now + 365L * 24 * 3600 * 1000, now)).isTrue()
        assertThat(SchedulePolicy.isAcceptable(now + 366L * 24 * 3600 * 1000, now)).isFalse()
    }

    @Test
    fun `quick options resolve to sensible local times`() {
        val options = SchedulePolicy.quickOptions(now, tehran)
        assertThat(options.map { it.labelKey }).containsExactly(
            ScheduleOption.Key.IN_ONE_HOUR,
            ScheduleOption.Key.TONIGHT,
            ScheduleOption.Key.TOMORROW_MORNING,
            ScheduleOption.Key.TOMORROW_EVENING,
        ).inOrder()
        fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(tehran)
        assertThat(local(options[1].atEpochMillis).toLocalTime()).isEqualTo(LocalTime.of(21, 0))
        assertThat(local(options[2].atEpochMillis).toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 6))
        assertThat(options.all { SchedulePolicy.isAcceptable(it.atEpochMillis, now) }).isTrue()
    }

    @Test
    fun `tonight disappears when it is almost now`() {
        val late = ZonedDateTime.of(2026, 10, 5, 20, 59, 30, 0, tehran).toInstant().toEpochMilli()
        assertThat(SchedulePolicy.quickOptions(late, tehran).map { it.labelKey }).doesNotContain(ScheduleOption.Key.TONIGHT)
    }
}
