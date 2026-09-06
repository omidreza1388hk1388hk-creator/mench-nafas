package com.omidgame.mench.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.omidgame.mench.MainActivity
import com.omidgame.mench.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives push notifications delivered while the app is backgrounded or
 * killed. Never invoked for a foreground WebSocket-delivered event — that
 * path is entirely separate (see ChatRepositoryImpl.handleRealtimeEvent);
 * this class only ever sees what NotificationsService (backend) decided
 * to actually send, which already excludes any recipient with an open
 * connection (see that service's isUserConnected check) — so there is no
 * "was this already shown via the socket" de-duplication to do here.
 */
@AndroidEntryPoint
class MenchFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokenRegistrar: PushTokenRegistrar

    // FirebaseMessagingService callbacks are not suspend functions and
    // must not block their calling thread — a dedicated scope, torn down
    // implicitly with the service process, mirrors the pattern already
    // used for ChatRepositoryImpl.repositoryScope for the same reason.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        serviceScope.launch { pushTokenRegistrar.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // The backend (see fcm-notification.provider.ts) always sends a
        // DATA-ONLY message on purpose, specifically so this callback is
        // reliably invoked in every app state instead of the OS silently
        // auto-displaying a notification (and skipping this method) for a
        // combined notification+data payload while backgrounded — see
        // that file's doc comment. title/body therefore always come from
        // `data`, not `message.notification` (which will be null here).
        val data = message.data
        val title = data["title"] ?: getString(R.string.app_name)
        val body = data["body"]?.takeIf { it.isNotEmpty() }
        val conversationId = data["conversationId"]

        showNotification(title = title, body = body, conversationId = conversationId, collapseKey = message.collapseKey)
    }

    private fun showNotification(title: String, body: String?, conversationId: String?, collapseKey: String?) {
        ensureChannel()

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (conversationId != null) putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
        }
        // requestCode derived from conversationId so a second notification
        // for the SAME conversation reuses/updates the same PendingIntent
        // instead of accumulating unrelated ones — collapseKey already
        // handles collapsing the visible notification itself (where the
        // backend/FCM support it); this keeps the tap target consistent
        // for whichever one ends up showing.
        val requestCode = (conversationId ?: collapseKey ?: title).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            // No dedicated monochrome status-bar icon exists yet — that's
            // visual-identity work (master-prompt sections 56/57),
            // explicitly out of scope for Phase 6. Using the launcher
            // icon here is functional but not final; swap for a proper
            // single-color notification icon when that phase lands.
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { if (conversationId != null) setGroup(conversationId) }
            .build()

        // Posting can throw SecurityException on API 33+ if the user has
        // denied POST_NOTIFICATIONS (or revoked it after granting) — that
        // is an entirely expected, non-fatal state (see MainActivity's
        // permission request), not a bug to crash over.
        try {
            NotificationManagerCompat.from(this).notify(requestCode, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted — the message was still
            // received and any Room/WebSocket state is unaffected; the
            // user simply won't see a system notification for it.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_messages), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    companion object {
        const val CHANNEL_ID = "messages"
    }
}
