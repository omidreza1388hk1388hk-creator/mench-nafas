package com.omidgame.mench.feature.appearance

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.omidgame.mench.core.ui.theme.GatedHapticFeedback
import com.omidgame.mench.feature.appearance.domain.BubbleStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleStyleAndHapticsTest {
    @Test
    fun newBubbleStylesRoundTripThroughTheirWireValue() {
        assertEquals(BubbleStyle.PILL, BubbleStyle.fromWireValue("pill"))
        assertEquals(BubbleStyle.LEAF, BubbleStyle.fromWireValue("leaf"))
        assertEquals(BubbleStyle.ROUNDED, BubbleStyle.fromWireValue("something-unknown"))
    }

    private class CountingHaptics : HapticFeedback {
        var count = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { count++ }
    }

    @Test
    fun gatedHapticsOnlyPassThroughWhileEnabled() {
        val inner = CountingHaptics()
        var enabled = true
        val gated = GatedHapticFeedback(inner) { enabled }
        gated.performHapticFeedback(HapticFeedbackType.LongPress)
        enabled = false
        gated.performHapticFeedback(HapticFeedbackType.LongPress)
        enabled = true
        gated.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        assertEquals(2, inner.count)
    }
}
