package com.omidgame.mench.core.webrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.omidgame.mench.feature.calls.domain.AudioRoute
import com.omidgame.mench.feature.calls.domain.AudioRoutePolicy
import com.omidgame.mench.feature.calls.domain.CallType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AudioRouteState(val available: List<AudioRoute>, val selected: AudioRoute)

/**
 * Phase 2B — chooses where call audio comes out: earpiece, speaker, Bluetooth or a wired headset.
 * Android 12+ uses setCommunicationDevice; older versions use the speakerphone / Bluetooth-SCO
 * switches. Watches device plug/unplug while a call is on and falls back sensibly (AudioRoutePolicy).
 */
@Singleton
class CallAudioRouter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(AudioRouteState(listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER), AudioRoute.EARPIECE))
    val state: StateFlow<AudioRouteState> = _state

    private var callType = CallType.VOICE
    private var running = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged(addedBluetoothOrWired = true)
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged(addedBluetoothOrWired = false)
    }

    fun start(type: CallType) {
        if (running) return
        running = true
        callType = type
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        val available = detectAvailable()
        val initial = AudioRoutePolicy.initial(type, available, CallSettingsStore.current.voiceStartsOnSpeaker)
        apply(initial)
        _state.value = AudioRouteState(available, initial)
    }

    fun select(route: AudioRoute) {
        if (!running || route !in _state.value.available) return
        apply(route)
        _state.value = _state.value.copy(selected = route)
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            run {
                audioManager.isSpeakerphoneOn = false
                if (audioManager.isBluetoothScoOn) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
            }
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        _state.value = AudioRouteState(listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER), AudioRoute.EARPIECE)
    }

    private fun onDevicesChanged(@Suppress("UNUSED_PARAMETER") addedBluetoothOrWired: Boolean) {
        if (!running) return
        val available = detectAvailable()
        val current = _state.value
        val next = if (current.available == available) {
            current.selected
        } else {
            // A headset appearing should take over the call (people expect that); a vanishing one falls back.
            val appeared = available.size > current.available.size
            if (appeared) AudioRoutePolicy.initial(callType, available) else AudioRoutePolicy.afterChange(current.selected, callType, available)
        }
        if (next != current.selected) apply(next)
        _state.value = AudioRouteState(available, next)
    }

    private fun detectAvailable(): List<AudioRoute> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasWired = devices.any { it.type in WIRED_TYPES }
        val btAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val hasBluetooth = btAllowed && devices.any { isBluetooth(it.type) }
        return AudioRoutePolicy.available(hasBluetooth, hasWired)
    }

    private fun apply(route: AudioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wanted: (AudioDeviceInfo) -> Boolean = when (route) {
                AudioRoute.EARPIECE -> { d -> d.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                AudioRoute.SPEAKER -> { d -> d.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                AudioRoute.BLUETOOTH -> { d -> isBluetooth(d.type) }
                AudioRoute.WIRED -> { d -> d.type in WIRED_TYPES }
            }
            val device = audioManager.availableCommunicationDevices.firstOrNull(wanted)
            if (device != null) audioManager.setCommunicationDevice(device) else audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            run {
                audioManager.isSpeakerphoneOn = route == AudioRoute.SPEAKER
                if (route == AudioRoute.BLUETOOTH) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                } else if (audioManager.isBluetoothScoOn) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
            }
        }
    }

    private fun isBluetooth(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    private companion object {
        val WIRED_TYPES = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET)
    }
}
