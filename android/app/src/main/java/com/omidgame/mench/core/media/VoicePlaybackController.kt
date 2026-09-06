package com.omidgame.mench.core.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.omidgame.mench.core.security.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Playing(val sourceId: String) : PlaybackState
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

    /** Prefers localPath (this device's own cached copy) over remoteUrl when both are available. */
    fun play(sourceId: String, localPath: String?, remoteUrl: String?) {
        stop()
        if (localPath == null && remoteUrl == null) return

        try {
            val player = MediaPlayer()
            if (localPath != null) {
                player.setDataSource(localPath)
                player.setOnPreparedListener { it.start() }
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
                player.setOnPreparedListener { it.start() }
                player.prepareAsync() // async: this is a network source, prepare() would block
            }
            player.setOnCompletionListener { stop() }
            mediaPlayer = player
            _state.value = PlaybackState.Playing(sourceId)
        } catch (e: Exception) {
            _state.value = PlaybackState.Idle
        }
    }

    fun stop() {
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

    fun togglePlay(sourceId: String, localPath: String?, remoteUrl: String?) {
        val current = _state.value
        if (current is PlaybackState.Playing && current.sourceId == sourceId) {
            stop()
        } else {
            play(sourceId, localPath, remoteUrl)
        }
    }
}
