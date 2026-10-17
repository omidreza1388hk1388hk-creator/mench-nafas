package com.omidgame.mench.core.media

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import com.omidgame.mench.core.security.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Playing(val sourceId: String) : PlaybackState
}

/**
 * Phase 2A — playhead of the voice message that is currently playing. Kept separate from
 * [PlaybackState] so that type (and every call site that matches on it) stays unchanged.
 * [sourceId] is null while nothing plays.
 */
data class PlaybackProgress(val sourceId: String? = null, val positionMs: Int = 0, val durationMs: Int = 0) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Plays exactly one voice message at a time app-wide — starting a new
 * playback stops whatever was already playing, matching how every
 * messenger's voice-note playback behaves.
 *
 * A received (not-yet-locally-cached) voice message streams straight from
 * `AttachmentUrls.content()`, which — like every other attachment
 * endpoint — requires the bearer token. MediaPlayer's plain
 * setDataSource(String) has no way to attach headers; the
 * setDataSource(Context, Uri, Map<String,String>) overload does, which is
 * why this takes a TokenStore rather than just a URL string.
 */
@Singleton
class VoicePlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
) {
    private var mediaPlayer: MediaPlayer? = null
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state

    // Phase 2A: playhead + playback speed (1x / 1.5x / 2x). Ticks on the main thread because
    // MediaPlayer is created and driven from there.
    private val tickScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    /** 1x -> 1.5x -> 2x -> 1x. Applied immediately if a message is playing, and remembered for the next one. */
    fun cycleSpeed() {
        _speed.value = when {
            _speed.value < 1.25f -> 1.5f
            _speed.value < 1.75f -> 2f
            else -> 1f
        }
        val player = mediaPlayer
        if (player != null && _state.value is PlaybackState.Playing) applySpeed(player)
    }

    private fun applySpeed(player: MediaPlayer) {
        try {
            // Per the MediaPlayer docs a non-zero speed on a prepared player also starts it —
            // callers only invoke this when playback is wanted (onPrepared, or while playing).
            player.playbackParams = PlaybackParams().setSpeed(_speed.value)
        } catch (e: Exception) {
            // IllegalStateException (released) / IllegalArgumentException (device rejects the rate): keep playing at 1x.
        }
    }

    private fun startTicker(sourceId: String, player: MediaPlayer) {
        tickJob?.cancel()
        tickJob = tickScope.launch {
            while (isActive && mediaPlayer === player) {
                val duration = try { player.duration } catch (e: Exception) { 0 }
                val position = try { player.currentPosition } catch (e: Exception) { 0 }
                _progress.value = PlaybackProgress(sourceId, position.coerceAtLeast(0), duration.coerceAtLeast(0))
                delay(TICK_MS)
            }
        }
    }

    /** Seeks the message that is playing; no-op if [sourceId] is not the one playing. */
    fun seekTo(sourceId: String, fraction: Float) {
        val current = _state.value
        val player = mediaPlayer
        if (player == null || current !is PlaybackState.Playing || current.sourceId != sourceId) return
        try {
            val duration = player.duration
            if (duration > 0) {
                val target = (fraction.coerceIn(0f, 1f) * duration).toInt()
                player.seekTo(target)
                _progress.value = PlaybackProgress(sourceId, target, duration)
            }
        } catch (e: Exception) {
            // Not prepared yet / released — ignore, the next tick restores the real position.
        }
    }

    /** Prefers localPath (this device's own cached copy) over remoteUrl when both are available. */
    fun play(sourceId: String, localPath: String?, remoteUrl: String?, startFraction: Float = 0f) {
        stop()
        if (localPath == null && remoteUrl == null) return

        try {
            val player = MediaPlayer()
            if (localPath != null) {
                player.setDataSource(localPath)
                player.setOnPreparedListener { onPrepared(sourceId, it, startFraction) }
                player.prepareAsync()
            } else {
                // runBlocking here mirrors AuthHeaderInterceptor's own use
                // of runBlocking to bridge a synchronous callback context
                // (MediaPlayer setup) with the suspend TokenStore read —
                // same justified exception to "no blocking calls", not a
                // new pattern invented for this file.
                val token = runBlocking { tokenStore.read() }?.accessToken
                val headers = if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
                player.setDataSource(context, Uri.parse(remoteUrl), headers)
                player.setOnPreparedListener { onPrepared(sourceId, it, startFraction) }
                player.prepareAsync() // async: this is a network source, prepare() would block
            }
            player.setOnCompletionListener { stop() }
            mediaPlayer = player
            _state.value = PlaybackState.Playing(sourceId)
        } catch (e: Exception) {
            _state.value = PlaybackState.Idle
        }
    }

    private fun onPrepared(sourceId: String, player: MediaPlayer, startFraction: Float) {
        // Ignore a late callback from a player that was already replaced / stopped.
        if (mediaPlayer !== player) return
        try {
            if (startFraction > 0f && player.duration > 0) {
                player.seekTo((startFraction.coerceIn(0f, 1f) * player.duration).toInt())
            }
            applySpeed(player)
            player.start()
            startTicker(sourceId, player)
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        _progress.value = PlaybackProgress()
        mediaPlayer?.apply {
            try {
                stop()
            } catch (e: Exception) {
                // Already stopped/never started — fine, we're releasing regardless.
            }
            release()
        }
        mediaPlayer = null
        _state.value = PlaybackState.Idle
    }

    /** Phase 2A: bearer-token headers for streamed media (round video playback). Blocking read of the token store — call off the main thread. */
    fun authHeaders(): Map<String, String> {
        val token = runBlocking { tokenStore.read() }?.accessToken
        return if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
    }

    /** Tap-and-drag on the waveform of a message that is not playing yet: start it at [fraction]. */
    fun playFrom(sourceId: String, localPath: String?, remoteUrl: String?, fraction: Float) {
        val current = _state.value
        if (current is PlaybackState.Playing && current.sourceId == sourceId) {
            seekTo(sourceId, fraction)
        } else {
            play(sourceId, localPath, remoteUrl, fraction)
        }
    }

    fun togglePlay(sourceId: String, localPath: String?, remoteUrl: String?) {
        val current = _state.value
        if (current is PlaybackState.Playing && current.sourceId == sourceId) {
            stop()
        } else {
            play(sourceId, localPath, remoteUrl)
        }
    }

    private companion object {
        const val TICK_MS = 40L
    }
}
