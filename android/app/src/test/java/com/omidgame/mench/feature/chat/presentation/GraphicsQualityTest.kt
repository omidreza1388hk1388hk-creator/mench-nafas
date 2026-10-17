package com.omidgame.mench.feature.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsQualityTest {
    @Test
    fun autoHalvesParticlesOnlyOnLowRamDevices() {
        assertEquals(0.5f, GraphicsQuality.AUTO.particleFactor(true), 0f)
        assertEquals(1f, GraphicsQuality.AUTO.particleFactor(false), 0f)
    }

    @Test
    fun lowAlwaysThinsAndHighNeverDoes() {
        assertEquals(0.35f, GraphicsQuality.LOW.particleFactor(false), 0f)
        assertEquals(1f, GraphicsQuality.HIGH.particleFactor(true), 0f)
    }

    @Test
    fun onlyLowTurnsOffLivingBackgrounds() {
        assertFalse(GraphicsQuality.LOW.allowsLivingBackground)
        assertTrue(GraphicsQuality.AUTO.allowsLivingBackground)
        assertTrue(GraphicsQuality.HIGH.allowsLivingBackground)
    }

    @Test
    fun imComingTextFillsTheMinutes() {
        assertEquals("On my way — about 20 min", imComingText("On my way — about %d min", 20))
    }
}
