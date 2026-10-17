package com.omidgame.mench.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.omidgame.mench.core.security.TokenStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** After a reboot, brings the connection service back — but only if someone is signed in. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var tokenStore: TokenStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (tokenStore.read() != null) {
                    runCatching {
                        ContextCompat.startForegroundService(context, Intent(context, RealtimeForegroundService::class.java))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
