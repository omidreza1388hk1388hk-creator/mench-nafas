package com.omidgame.mench.feature.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoupleGamesTest {
    @Test
    fun theTenPairsCoverEveryCombinationOfTwoLanguagesOnce() {
        assertEquals(10, lovePairs.size)
        val combos = lovePairs.map { setOf(it.first, it.second) }
        assertEquals(10, combos.toSet().size)
        assertTrue(combos.all { it.size == 2 })
    }

    @Test
    fun scoringCountsThePickedLanguage() {
        // Always choosing the first option.
        val scores = scoreLoveLanguages(List(10) { true })
        assertEquals(10, scores.values.sum())
        assertEquals(3, scores[LoveLanguage.TIME])
        assertEquals(2, scores[LoveLanguage.WORDS])
    }

    @Test
    fun topLanguagesHandlesTiesAndEmpty() {
        assertEquals(listOf(LoveLanguage.TIME), topLoveLanguages(scoreLoveLanguages(List(10) { true })))
        assertEquals(emptyList<LoveLanguage>(), topLoveLanguages(scoreLoveLanguages(emptyList())))
        val tie = mapOf(LoveLanguage.WORDS to 4, LoveLanguage.ACTS to 4, LoveLanguage.TIME to 2)
        assertEquals(listOf(LoveLanguage.WORDS, LoveLanguage.ACTS), topLoveLanguages(tie))
    }

    @Test
    fun buildQuestionDropsBlankOptionsAndFollowsTheRightAnswer() {
        val q = buildQuestion(QuizDraft("Colour?", listOf("Red", "", "Blue"), correct = 2))
        assertEquals(listOf("Red", "Blue"), q?.options)
        assertEquals(1, q?.correct)
    }

    @Test
    fun buildQuestionRejectsIncompleteDrafts() {
        assertNull(buildQuestion(QuizDraft("", listOf("a", "b", "c"), 0)))
        assertNull(buildQuestion(QuizDraft("Q", listOf("a", "", ""), 0)))
        assertNull(buildQuestion(QuizDraft("Q", listOf("a", "b", ""), 2)))
    }
}
