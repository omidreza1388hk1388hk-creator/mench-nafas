package com.omidgame.mench.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.omidgame.mench.feature.chat.domain.ChatRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "reply" box of a message notification: sends the typed text into
 * that conversation through the normal outbox (so it is queued and retried like
 * any message) and clears the notification.
 */
class QuickReplyReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun chatRepository(): ChatRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (text.isNullOrEmpty()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
                    .chatRepository()
                    .sendMessage(conversationId, text, null)
            } finally {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
                pending.finish()
            }
        }
    }

    companion object {
        const val KEY_REPLY_TEXT = "reply_text"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
