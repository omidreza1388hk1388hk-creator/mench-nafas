package com.omidgame.mench.core.ui.a11y

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MenchA11yTest {

    @Test
    fun `black on white is the maximum contrast`() {
        assertThat(MenchA11y.contrastRatio(Color.Black, Color.White)).isWithin(0.05f).of(21f)
    }

    @Test
    fun `identical colors have contrast 1`() {
        assertThat(MenchA11y.contrastRatio(Color.Red, Color.Red)).isWithin(0.001f).of(1f)
    }

    @Test
    fun `dark backgrounds get white text`() {
        assertThat(MenchA11y.readableOn(Color(0xFF3B0F24))).isEqualTo(Color.White)
    }

    @Test
    fun `light backgrounds get dark text`() {
        val ink = MenchA11y.readableOn(Color(0xFFFFE9A8))
        assertThat(ink).isNotEqualTo(Color.White)
        assertThat(MenchA11y.contrastRatio(ink, Color(0xFFFFE9A8))).isAtLeast(MenchA11y.AA_NORMAL_TEXT)
    }

    @Test
    fun `chosen text color never has worse contrast than the alternative`() {
        listOf(0xFFE91E63, 0xFF3F51B5, 0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFFF44336).forEach { argb ->
            val bg = Color(argb)
            val picked = MenchA11y.readableOn(bg)
            val other = if (picked == Color.White) Color(0xFF1B1B1F) else Color.White
            assertThat(MenchA11y.contrastRatio(picked, bg))
                .isAtLeast(minOf(MenchA11y.contrastRatio(other, bg), MenchA11y.AA_NORMAL_TEXT))
        }
    }
}
