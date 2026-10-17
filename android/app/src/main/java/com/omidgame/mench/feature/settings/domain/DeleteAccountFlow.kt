package com.omidgame.mench.feature.settings.domain

/** Steps of the delete-account flow, in order. */
enum class DeleteStep { INFO, CONFIRM, COOLING, FINAL }

object DeleteAccountFlow {
    /** The word the user must type. Kept identical to what the backend expects in the request body. */
    const val CONFIRM_WORD = "DELETE"

    /** Seconds the user must wait on the "think again" step before they can continue. */
    const val COOLING_SECONDS = 10

    val stepCount: Int = DeleteStep.entries.size

    fun next(step: DeleteStep): DeleteStep? = DeleteStep.entries.getOrNull(step.ordinal + 1)
    fun previous(step: DeleteStep): DeleteStep? = DeleteStep.entries.getOrNull(step.ordinal - 1)

    fun typedConfirmationValid(typed: String): Boolean =
        typed.trim().equals(CONFIRM_WORD, ignoreCase = true)

    /** Whether the Continue button of [step] is enabled. */
    fun canContinue(step: DeleteStep, typed: String, secondsLeft: Int): Boolean = when (step) {
        DeleteStep.INFO -> true
        DeleteStep.CONFIRM -> typedConfirmationValid(typed)
        DeleteStep.COOLING -> secondsLeft <= 0
        DeleteStep.FINAL -> false // the final step has its own "verify and delete" button
    }
}
