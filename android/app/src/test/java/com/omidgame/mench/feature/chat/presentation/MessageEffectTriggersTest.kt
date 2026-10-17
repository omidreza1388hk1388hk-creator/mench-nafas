package com.omidgame.mench.feature.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageEffectTriggersTest {
    @Test
    fun builtInPersianAndEnglishPhrases() {
        assertEquals(MessageEffect.HEARTS, MessageEffectTriggers.detect("دوستت دارم!"))
        assertEquals(MessageEffect.NIGHT, MessageEffectTriggers.detect("شب بخیر عزیزم"))
        assertEquals(MessageEffect.BALLOONS, MessageEffectTriggers.detect("تولدت مبارک"))
        assertEquals(MessageEffect.CONFETTI, MessageEffectTriggers.detect("Congrats!!"))
        assertEquals(MessageEffect.LANTERNS, MessageEffectTriggers.detect("I miss you so much"))
    }

    @Test
    fun birthdayBeatsGenericCongratulations() {
        assertEquals(MessageEffect.BALLOONS, MessageEffectTriggers.detect("تولدت مبارک"))
    }

    @Test
    fun requiresWholePhraseNotASubstring() {
        assertNull(MessageEffectTriggers.detect("lovely day"))
        assertNull(MessageEffectTriggers.detect(""))
        assertNull(MessageEffectTriggers.detect(null))
        assertNull(MessageEffectTriggers.detect("x".repeat(500) + " good night"))
    }

    @Test
    fun arabicLettersAreUnifiedWithPersian() {
        // "بخير" written with an Arabic yeh (U+064A) must match the Persian phrase "شب بخیر".
        val arabicYeh = "\u0634\u0628 \u0628\u062E\u064A\u0631"
        assertEquals(MessageEffect.NIGHT, MessageEffectTriggers.detect(arabicYeh))
    }

    @Test
    fun customPhrasesWinOverBuiltIns() {
        val custom = listOf(MessageEffect.CONFETTI to MessageEffectTriggers.normalize("our song").trim())
        assertEquals(MessageEffect.CONFETTI, MessageEffectTriggers.detect("playing OUR SONG now", custom))
        assertEquals(MessageEffect.HEARTS, MessageEffectTriggers.detect("i love you", custom))
    }
}
