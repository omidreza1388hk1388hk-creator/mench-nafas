package com.omidgame.mench.feature.privacy.domain

/** Disappearing-message presets, in seconds. 0 = off. Mirrors the backend range (60 s .. 1 year). */
object DisappearingPresets {
    const val OFF = 0
    const val ONE_HOUR = 3_600
    const val ONE_DAY = 86_400
    const val ONE_WEEK = 604_800
    const val THIRTY_DAYS = 2_592_000

    val all: List<Int> = listOf(OFF, ONE_HOUR, ONE_DAY, ONE_WEEK, THIRTY_DAYS)

    /** True when [seconds] is one of the presets (anything else is shown as a custom timer). */
    fun isPreset(seconds: Int?): Boolean = (seconds ?: OFF) in all
}

/** Same rules as backend two-step.policy.ts, so the user hears "too short" before a network round trip. */
object TwoStepValidation {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 64

    enum class Problem { TOO_SHORT, TOO_LONG, REPEATED, MISMATCH, HINT_CONTAINS_PASSWORD }

    fun check(password: String, confirm: String, hint: String): Problem? = when {
        password.length < MIN_LENGTH -> Problem.TOO_SHORT
        password.length > MAX_LENGTH -> Problem.TOO_LONG
        password.all { it == password.first() } -> Problem.REPEATED
        password != confirm -> Problem.MISMATCH
        hint.isNotBlank() && hint.contains(password, ignoreCase = true) -> Problem.HINT_CONTAINS_PASSWORD
        else -> null
    }
}
