package com.omidgame.mench.feature.calls.domain

enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED }

/** Phase 2B — which output a call should use, given what is currently plugged in / paired. Pure, unit-tested. */
object AudioRoutePolicy {
    /** Earpiece and speaker are always available on a phone. */
    fun available(hasBluetooth: Boolean, hasWired: Boolean): List<AudioRoute> = buildList {
        add(AudioRoute.EARPIECE)
        add(AudioRoute.SPEAKER)
        if (hasBluetooth) add(AudioRoute.BLUETOOTH)
        if (hasWired) add(AudioRoute.WIRED)
    }

    /** A headset always wins; otherwise video calls start on the speaker and voice calls at the ear. */
    fun initial(callType: CallType, available: List<AudioRoute>, voiceOnSpeaker: Boolean = false): AudioRoute = when {
        AudioRoute.WIRED in available -> AudioRoute.WIRED
        AudioRoute.BLUETOOTH in available -> AudioRoute.BLUETOOTH
        callType == CallType.VIDEO || voiceOnSpeaker -> AudioRoute.SPEAKER // Settings Phase 2: voice may start on speaker
        else -> AudioRoute.EARPIECE
    }

    /** When the current route disappears (headset unplugged, Bluetooth off) fall back sensibly instead of leaving the call silent. */
    fun afterChange(current: AudioRoute, callType: CallType, available: List<AudioRoute>): AudioRoute =
        if (current in available) current else initial(callType, available)

    /** Proximity screen-off only makes sense with the phone at the ear. */
    fun usesProximity(route: AudioRoute, callType: CallType): Boolean = route == AudioRoute.EARPIECE && callType == CallType.VOICE
}
