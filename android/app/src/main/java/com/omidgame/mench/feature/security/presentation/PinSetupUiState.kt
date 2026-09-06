package com.omidgame.mench.feature.security.presentation

enum class PinSetupMode { CREATE, CHANGE, DISABLE }

sealed interface PinSetupStep {
    /** CHANGE and DISABLE both start by proving the user still knows the current PIN. */
    data object EnterCurrent : PinSetupStep
    data object EnterNew : PinSetupStep
    data object ConfirmNew : PinSetupStep
}

data class PinSetupUiState(
    val mode: PinSetupMode,
    val step: PinSetupStep,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    /** Set once the flow's terminal action (save/disable) has actually completed — the nav graph pops back to Privacy & Security when this flips true. */
    val completed: Boolean = false,
) {
    companion object {
        fun initial(mode: PinSetupMode): PinSetupUiState = PinSetupUiState(
            mode = mode,
            step = if (mode == PinSetupMode.CREATE) PinSetupStep.EnterNew else PinSetupStep.EnterCurrent,
        )
    }
}
