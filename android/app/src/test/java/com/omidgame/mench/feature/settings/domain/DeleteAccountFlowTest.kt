package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeleteAccountFlowTest {
    @Test fun `steps go forward and back and stop at the ends`() {
        assertThat(DeleteAccountFlow.next(DeleteStep.INFO)).isEqualTo(DeleteStep.CONFIRM)
        assertThat(DeleteAccountFlow.next(DeleteStep.COOLING)).isEqualTo(DeleteStep.FINAL)
        assertThat(DeleteAccountFlow.next(DeleteStep.FINAL)).isNull()
        assertThat(DeleteAccountFlow.previous(DeleteStep.INFO)).isNull()
        assertThat(DeleteAccountFlow.previous(DeleteStep.FINAL)).isEqualTo(DeleteStep.COOLING)
    }

    @Test fun `typed confirmation needs the exact word ignoring case and spaces`() {
        assertThat(DeleteAccountFlow.typedConfirmationValid("DELETE")).isTrue()
        assertThat(DeleteAccountFlow.typedConfirmationValid(" delete ")).isTrue()
        assertThat(DeleteAccountFlow.typedConfirmationValid("DELET")).isFalse()
        assertThat(DeleteAccountFlow.typedConfirmationValid("")).isFalse()
    }

    @Test fun `continue rules per step`() {
        assertThat(DeleteAccountFlow.canContinue(DeleteStep.INFO, "", 10)).isTrue()
        assertThat(DeleteAccountFlow.canContinue(DeleteStep.CONFIRM, "nope", 10)).isFalse()
        assertThat(DeleteAccountFlow.canContinue(DeleteStep.CONFIRM, "DELETE", 10)).isTrue()
        assertThat(DeleteAccountFlow.canContinue(DeleteStep.COOLING, "DELETE", 3)).isFalse()
        assertThat(DeleteAccountFlow.canContinue(DeleteStep.COOLING, "DELETE", 0)).isTrue()
        assertThat(DeleteAccountFlow.canContinue(DeleteStep.FINAL, "DELETE", 0)).isFalse()
    }

    @Test fun `confirm word matches what the backend expects`() {
        assertThat(DeleteAccountFlow.CONFIRM_WORD).isEqualTo("DELETE")
        assertThat(DeleteAccountFlow.stepCount).isEqualTo(4)
    }
}
