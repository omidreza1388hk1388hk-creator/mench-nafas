package com.omidgame.mench.feature.settings.domain

/** How strong hug / kiss / heartbeat vibrations feel. NORMAL is exactly the pattern the app always used. */
enum class HapticStrength(val storageValue: String) {
    SOFT("soft"), NORMAL("normal"), STRONG("strong");

    companion object { fun fromStorage(v: String?) = entries.firstOrNull { it.storageValue == v } ?: NORMAL }
}

/** Settings of the couple ("Us") features. Defaults keep every feature visible and every effect as before. */
data class UsSettingsSnapshot(
    val moodEnabled: Boolean = true,
    val questionEnabled: Boolean = true,
    val wishlistEnabled: Boolean = true,
    val memoriesEnabled: Boolean = true,
    val hapticStrength: HapticStrength = HapticStrength.NORMAL,
    val heartsAnimation: Boolean = true,
)

object AffectionHaptics {
    private const val SOFT_AMPLITUDE = 60
    private const val STRONG_AMPLITUDE = 255

    /**
     * Per-step amplitudes for a waveform whose [timings] alternate pause, buzz, pause, buzz... (index 0 is a pause).
     * Null for NORMAL: the caller then uses the original timing-only waveform, so default behaviour is untouched.
     */
    fun amplitudes(timingCount: Int, strength: HapticStrength): IntArray? {
        val level = when (strength) {
            HapticStrength.NORMAL -> return null
            HapticStrength.SOFT -> SOFT_AMPLITUDE
            HapticStrength.STRONG -> STRONG_AMPLITUDE
        }
        return IntArray(timingCount) { i -> if (i % 2 == 0) 0 else level }
    }
}
