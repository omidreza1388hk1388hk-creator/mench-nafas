package com.omidgame.mench.core.ui.adaptive

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdaptiveTest {
    @Test
    fun `phones and small tablets stay single pane`() {
        assertThat(isTwoPane(360)).isFalse()
        assertThat(isTwoPane(600)).isFalse()
        assertThat(isTwoPane(839)).isFalse()
    }

    @Test
    fun `expanded widths get two panes`() {
        assertThat(isTwoPane(840)).isTrue()
        assertThat(isTwoPane(1280)).isTrue()
    }
}
