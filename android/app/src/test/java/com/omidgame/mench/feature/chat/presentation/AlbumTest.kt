package com.omidgame.mench.feature.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumTest {
    @Test
    fun tappingYourOwnReactionAgainRemovesIt() {
        assertEquals("💗", nextReaction(null, "💗"))
        assertEquals("😍", nextReaction("💗", "😍"))
        assertNull(nextReaction("💗", "💗"))
    }
}
