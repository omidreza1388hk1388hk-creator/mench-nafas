package com.omidgame.mench.feature.settings.domain

/** Sound options on top of the existing on/off switch. Pure rules, JVM-testable. */
object SoundSettingsRules {
    const val MIN_VOLUME = 0.1f
    const val MAX_VOLUME = 1f
    const val DEFAULT_VOLUME = 0.7f // the level the app always played at

    fun clampVolume(v: Float): Float = v.coerceIn(MIN_VOLUME, MAX_VOLUME)

    /** Pref key for one event's switch; derived from the enum name so a new sound needs no extra code. */
    fun eventKey(soundName: String): String = "event_" + soundName.lowercase()

    /** A sound plays only when the master switch AND its own event switch are on. */
    fun shouldPlay(masterEnabled: Boolean, eventEnabled: Boolean): Boolean = masterEnabled && eventEnabled
}

/** Text weight choice for the app font. Letter spacing is intentionally not offered: it breaks Persian letter joining. */
enum class TextWeightChoice(val storageValue: String) {
    REGULAR("regular"), MEDIUM("medium");

    companion object { fun fromStorage(v: String?) = entries.firstOrNull { it.storageValue == v } ?: REGULAR }
}
