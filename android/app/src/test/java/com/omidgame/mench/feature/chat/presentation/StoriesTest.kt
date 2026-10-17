package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Story
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoriesTest {
    private fun story(id: String, mine: Boolean = false, seen: Boolean = false) = Story(id, "b", "", 0, mine, seen, 0)

    @Test
    fun startsAtTheFirstUnseenStoryFromOthers() {
        assertEquals(1, firstUnseenIndex(listOf(story("a", seen = true), story("b"), story("c"))))
    }

    @Test
    fun ownStoriesAndAllSeenFallBackToTheFirst() {
        assertEquals(0, firstUnseenIndex(listOf(story("a", mine = true), story("b", seen = true))))
        assertEquals(0, firstUnseenIndex(emptyList()))
    }

    @Test
    fun steppingStopsAtBothEnds() {
        assertEquals(2, stepStory(1, 1, 3))
        assertEquals(0, stepStory(1, -1, 3))
        assertNull(stepStory(2, 1, 3))
        assertNull(stepStory(0, -1, 3))
    }
}
