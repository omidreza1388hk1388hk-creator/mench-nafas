package com.omidgame.mench.feature.chat.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopInTest {
    @Test
    fun onlyJustCreatedMessagesArePoppedIn() {
        assertTrue(isFreshMessage(createdAtMillis = 10_000, nowMillis = 11_000))
        assertTrue(isFreshMessage(10_000, 12_500))
        assertFalse(isFreshMessage(10_000, 12_501))
        assertFalse(isFreshMessage(10_000, 9_000)) // a timestamp from the future is not fresh
    }
}
