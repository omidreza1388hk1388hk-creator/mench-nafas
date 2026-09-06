package com.omidgame.mench.core.network.realtime

import com.omidgame.mench.BuildConfig
import com.omidgame.mench.core.network.RealtimeSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
}

/**
 * Owns exactly one live WebSocket connection to /ws, with reconnect-with-
 * backoff on unexpected disconnect. Does NOT auto-reconnect on token
 * expiry mid-connection — the gateway only validates the access token at
 * connect time (see backend/src/modules/realtime/chat.gateway.ts), so a
 * long-lived idle connection can outlive its token's nominal TTL. That's
 * an accepted Phase 2 limitation, not a silent gap: session revocation
 * (logout-all-devices) still works, since a revoked *refresh* token
 * prevents getting a new access token, it just doesn't retroactively
 * close already-open sockets. Documented in docs/SECURITY.md.
 */
@Singleton
class RealtimeClient @Inject constructor(
    @RealtimeSocketClient private val okHttpClient: OkHttpClient,
    private val eventParser: RealtimeEventParser,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentAccessToken: String? = null
    private var manuallyClosed = false
    private var attempt = 0

    private val _events = MutableSharedFlow<ServerToClientEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServerToClientEvent> = _events

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1, extraBufferCapacity = 1)
    val connectionState: SharedFlow<ConnectionState> = _connectionState

    fun connect(accessToken: String) {
        manuallyClosed = false
        currentAccessToken = accessToken
        attempt = 0
        openSocket()
    }

    fun disconnect() {
        manuallyClosed = true
        reconnectJob?.cancel()
        socket?.close(1000, "client disconnect")
        socket = null
    }

    /** Generic outgoing-frame send — covers typing state and call signaling (offer/answer/ICE/hangup) alike, both plain fire-and-forget frames over the same socket. */
    fun send(event: ClientToServerEvent) {
        socket?.send(eventParser.serialize(event))
    }

    private fun openSocket() {
        val token = currentAccessToken ?: return
        _connectionState.tryEmit(ConnectionState.Connecting)

        val wsUrl = apiBaseUrlToWsUrl(BuildConfig.API_BASE_URL) + "ws?token=$token"
        val request = Request.Builder().url(wsUrl).build()

        socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
                _connectionState.tryEmit(ConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                eventParser.parse(text)?.let { _events.tryEmit(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.tryEmit(ConnectionState.Disconnected)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.tryEmit(ConnectionState.Disconnected)
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (manuallyClosed) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            attempt++
            // Exponential backoff, capped at 30s: 1s, 2s, 4s, 8s, 16s, 30s, 30s...
            val delayMs = min(30_000L, (1000L * 2.0.pow(attempt - 1)).toLong())
            delay(delayMs)
            if (!manuallyClosed) openSocket()
        }
    }

    /** BuildConfig.API_BASE_URL is like "http://10.0.2.2:3000/api/v1/" — the WS endpoint is host-relative, not under /api/v1 (see main.ts). */
    private fun apiBaseUrlToWsUrl(apiBaseUrl: String): String {
        val httpBase = apiBaseUrl.substringBefore("/api/v1")
        return httpBase.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/"
    }
}
