package com.omidgame.mench.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkKind { NONE, WIFI, MOBILE, ROAMING }

/**
 * Phase 1B: lightweight connectivity monitor. Registered lazily on first access so it costs
 * nothing during cold start (startup rule: lazy-init non-critical singletons).
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: ConnectivityManager? by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private val _kind = MutableStateFlow(NetworkKind.NONE)
    private val _online = MutableStateFlow(true)
    private var registered = false

    val kind: StateFlow<NetworkKind> get() { ensureRegistered(); return _kind }
    val isOnline: StateFlow<Boolean> get() { ensureRegistered(); return _online }

    /** Called by RealtimeClient users when connectivity returns, e.g. to retry immediately. */
    @Volatile var onNetworkAvailable: (() -> Unit)? = null

    @Synchronized
    private fun ensureRegistered() {
        if (registered) return
        registered = true
        val cm = manager ?: return
        refresh(cm.activeNetwork, cm)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refresh(network, cm)
                    onNetworkAvailable?.invoke()
                }

                override fun onLost(network: Network) {
                    _kind.value = NetworkKind.NONE
                    _online.value = false
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    refresh(network, cm)
                }
            })
        }
    }

    private fun refresh(network: Network?, cm: ConnectivityManager) {
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _online.value = online
        _kind.value = when {
            !online || caps == null -> NetworkKind.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.WIFI
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) -> NetworkKind.ROAMING
            else -> NetworkKind.MOBILE
        }
    }
}

/**
 * One-shot, stateless read of the current connection type (same mapping as [NetworkMonitor]).
 * Used by UI code that only needs the answer at composition time, e.g. the media download gate.
 */
fun currentNetworkKind(context: Context): NetworkKind {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NetworkKind.NONE
    val network = cm.activeNetwork ?: return NetworkKind.NONE
    val caps = cm.getNetworkCapabilities(network) ?: return NetworkKind.NONE
    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkKind.NONE
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.WIFI
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) -> NetworkKind.ROAMING
        else -> NetworkKind.MOBILE
    }
}
