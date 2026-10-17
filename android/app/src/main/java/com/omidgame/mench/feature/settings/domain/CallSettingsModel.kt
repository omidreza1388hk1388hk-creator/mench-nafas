package com.omidgame.mench.feature.settings.domain

enum class CallQualityPreset(val storageValue: String) {
    AUTO("auto"), DATA_SAVER("data_saver"), HIGH("high");

    companion object { fun fromStorage(v: String?) = entries.firstOrNull { it.storageValue == v } ?: AUTO }

    /**
     * Applies the preset to the bitrate ceiling chosen for the measured quality tier. AUTO leaves it untouched,
     * DATA_SAVER never exceeds 400 kbit/s, HIGH allows up to 2.5 Mbit/s but never RAISES a ceiling the network
     * measurement already lowered (weak networks stay protected).
     */
    fun apply(tierCeilingBps: Int, goodCeilingBps: Int): Int = when (this) {
        AUTO -> tierCeilingBps
        DATA_SAVER -> minOf(tierCeilingBps, DATA_SAVER_CAP_BPS)
        HIGH -> if (tierCeilingBps >= goodCeilingBps) HIGH_CAP_BPS else tierCeilingBps
    }

}

private const val DATA_SAVER_CAP_BPS = 400_000
private const val HIGH_CAP_BPS = 2_500_000

/** Settings of voice and video calls. Defaults keep today's behaviour. */
data class CallSettingsSnapshot(
    val qualityPreset: CallQualityPreset = CallQualityPreset.AUTO,
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val voiceStartsOnSpeaker: Boolean = false,
    val frontCameraDefault: Boolean = true,
    val mirrorFrontCamera: Boolean = true,
)
