package com.omidgame.mench.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.network.NetworkMonitor
import com.omidgame.mench.core.network.realtime.ConnectionState
import com.omidgame.mench.core.network.realtime.RealtimeClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the connection banner should say. HIDDEN when everything is fine. */
enum class ConnectionBanner { HIDDEN, CONNECTING, WAITING_FOR_NETWORK }

/**
 * Phase 1B. Turns (socket state, device connectivity) into one banner value and kicks an
 * immediate reconnect when the network comes back.
 */
@HiltViewModel
class ConnectionStatusViewModel @Inject constructor(
    private val realtimeClient: RealtimeClient,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    val banner: StateFlow<ConnectionBanner> = combine(
        realtimeClient.connectionState,
        networkMonitor.isOnline,
    ) { socket, online ->
        when {
            !online -> ConnectionBanner.WAITING_FOR_NETWORK
            socket is ConnectionState.Connected -> ConnectionBanner.HIDDEN
            else -> ConnectionBanner.CONNECTING
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionBanner.HIDDEN)

    init {
        viewModelScope.launch {
            // When connectivity returns, do not wait out the remaining backoff delay.
            networkMonitor.isOnline.collect { online -> if (online) realtimeClient.retryNow() }
        }
    }
}
