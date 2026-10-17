package com.omidgame.mench.core.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TypedConfirmTest {
    @Test fun `exact word matches`() = assertThat(TypedConfirm.matches("DELETE", "DELETE")).isTrue()
    @Test fun `case and surrounding spaces are ignored`() = assertThat(TypedConfirm.matches("  delete ", "DELETE")).isTrue()
    @Test fun `different word does not match`() = assertThat(TypedConfirm.matches("DELET", "DELETE")).isFalse()
    @Test fun `empty typed text never matches`() = assertThat(TypedConfirm.matches("", "DELETE")).isFalse()
    @Test fun `blank required word never matches`() = assertThat(TypedConfirm.matches("", "")).isFalse()
}
