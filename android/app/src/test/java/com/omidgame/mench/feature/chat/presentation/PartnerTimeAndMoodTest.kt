package com.omidgame.mench.feature.chat.presentation

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PartnerTimeAndMoodTest {
    private val winter = Instant.parse("2026-01-15T12:00:00Z")

    @Test
    fun offsetBetweenZones() {
        assertEquals(210, offsetMinutes(ZoneId.of("UTC"), ZoneId.of("Asia/Tehran"), Instant.parse("2026-01-15T12:00:00Z")))
        assertEquals(-300, offsetMinutes(ZoneId.of("UTC"), ZoneId.of("America/New_York"), winter))
        assertEquals(0, offsetMinutes(ZoneId.of("Europe/Paris"), ZoneId.of("Europe/Berlin"), winter))
    }

    @Test
    fun formatsHoursAndMinutes() {
        assertEquals("3", formatHoursMinutes(180))
        assertEquals("3:30", formatHoursMinutes(-210))
        assertEquals("0", formatHoursMinutes(0))
    }

    @Test
    fun cityNames() {
        assertEquals("Tehran", cityName("Asia/Tehran"))
        assertEquals("New York", cityName("America/New_York"))
    }

    @Test
    fun everyOfferedZoneIsValid() {
        partnerZones.forEach { ZoneId.of(it) }
    }

    @Test
    fun moodDuetUsesBothMoodsAndAFallback() {
        assertNotEquals(moodColor("😡"), moodColor("😴"))
        assertEquals(listOf(moodColor("🥰"), moodColor("😢")), duetColors("🥰", "😢"))
        assertEquals(Color(0xFFFF9EB5), moodColor("🦄"))
    }
}
