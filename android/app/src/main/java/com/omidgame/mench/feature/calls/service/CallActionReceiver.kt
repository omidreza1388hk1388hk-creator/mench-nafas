package com.omidgame.mench.feature.calls.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.omidgame.mench.feature.calls.domain.CallSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * @AndroidEntryPoint on a BroadcastReceiver gets Hilt field injection
 * working here the same way it does on the Activity/Service — needed
 * because CallSessionManager (a plain @Singleton, not tied to any
 * Android component) has no other entry point reachable from a
 * PendingIntent-triggered broadcast.
 */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {

    @Inject lateinit var callSessionManager: CallSessionManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ACCEPT -> callSessionManager.acceptIncomingCall()
            ACTION_DECLINE -> callSessionManager.declineIncomingCall()
            ACTION_HANGUP -> callSessionManager.hangUp()
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.omidgame.mench.calls.ACCEPT"
        const val ACTION_DECLINE = "com.omidgame.mench.calls.DECLINE"
        const val ACTION_HANGUP = "com.omidgame.mench.calls.HANGUP"
    }
}
