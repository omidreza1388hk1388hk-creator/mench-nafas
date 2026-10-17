package com.omidgame.mench.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.omidgame.mench.feature.chat.domain.ChatRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** "Mark as read" action on a message notification: acks up to the notified message and dismisses it. */
class MarkReadReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun chatRepository(): ChatRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val sequence = intent.getLongExtra(EXTRA_SEQUENCE, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (sequence >= 0) {
                    EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
                        .chatRepository()
                        .markRead(conversationId, sequence)
                }
            } finally {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_SEQUENCE = "sequence"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
