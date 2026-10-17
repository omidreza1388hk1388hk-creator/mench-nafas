package com.omidgame.mench.feature.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsHelpersTest {
    @Test
    fun checkMarkDrawsShortStrokeFirstThenLong() {
        assertEquals(2, checkProgressPoints(0.2f).size)
        assertEquals(3, checkProgressPoints(0.7f).size)
        val full = checkProgressPoints(1f)
        assertEquals(0.80f, full.last().first, 0.0001f)
        assertEquals(0.30f, full.last().second, 0.0001f)
        assertEquals(0.20f to 0.55f, checkProgressPoints(0f).first())
    }

    @Test
    fun checkProgressIsClamped() {
        assertEquals(checkProgressPoints(1f), checkProgressPoints(5f))
        assertEquals(checkProgressPoints(0f), checkProgressPoints(-2f))
    }

    @Test
    fun dragDismissProgressAndThreshold() {
        assertEquals(0f, dragDismissProgress(0f), 0f)
        assertEquals(0.5f, dragDismissProgress(300f), 0.0001f)
        assertEquals(0.5f, dragDismissProgress(-300f), 0.0001f)
        assertEquals(1f, dragDismissProgress(5000f), 0f)
        assertFalse(shouldDismissDrag(219f))
        assertTrue(shouldDismissDrag(-220f))
    }

    @Test
    fun panIsClampedToWhatTheZoomedImageCanReach() {
        assertEquals(0f, clampPan(50f, 1f, 1000f), 0f)
        assertEquals(500f, clampPan(900f, 2f, 1000f), 0f)
        assertEquals(-500f, clampPan(-900f, 2f, 1000f), 0f)
        assertEquals(120f, clampPan(120f, 2f, 1000f), 0f)
    }
}
