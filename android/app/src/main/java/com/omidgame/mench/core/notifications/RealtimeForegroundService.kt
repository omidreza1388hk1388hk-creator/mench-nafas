package com.omidgame.mench.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.omidgame.mench.MainActivity
import com.omidgame.mench.R
import com.omidgame.mench.feature.settings.domain.NotificationType
import com.omidgame.mench.core.database.ConversationDao
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.UsersApi
import com.omidgame.mench.core.network.realtime.RealtimeClient
import com.omidgame.mench.core.network.realtime.ServerToClientEvent
import com.omidgame.mench.core.security.TokenStore
import com.omidgame.mench.feature.chat.domain.ChatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the app's single WebSocket alive while the app is in the background,
 * so messages (and hugs, kisses, letters, "I miss you") arrive and notify
 * even when Firebase push is blocked or flaky — which it often is in Iran.
 *
 * Why this also needs to show notifications itself: the backend skips FCM
 * for any user it sees as connected (NotificationsService.isUserConnected),
 * and this service is exactly what makes the user "connected". If the
 * process or socket dies, the server sees them offline and falls back to
 * FCM automatically — the two paths never both fire for the same event.
 *
 * Foreground type specialUse: dataSync is capped (a few hours per day on
 * Android 15), a chat connection is not. Fine for a sideloaded APK; a Play
 * Store release would need to justify it in the declaration form.
 */
@AndroidEntryPoint
class RealtimeForegroundService : Service() {

    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var realtimeClient: RealtimeClient
    @Inject lateinit var tokenStore: TokenStore
    @Inject lateinit var usersApi: UsersApi
    @Inject lateinit var chatApi: ChatApi
    @Inject lateinit var conversationDao: ConversationDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventJob: Job? = null
    private var privacyMode: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels()
        val notification = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ONGOING_ID, notification)
        }

        // Fresh token for every reconnect: calling any authenticated endpoint makes the
        // OkHttp authenticator refresh an expired access token; we then read the stored one.
        realtimeClient.accessTokenProvider = {
            runCatching { usersApi.getMe() }
            tokenStore.read()?.accessToken
        }
        chatRepository.connectRealtime()

        if (eventJob?.isActive != true) {
            eventJob = scope.launch { realtimeClient.events.collect { handleEvent(it) } }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        eventJob?.cancel()
        scope.cancel()
        realtimeClient.accessTokenProvider = null
        super.onDestroy()
    }

    private val appInForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    @Inject lateinit var notificationPolicy: NotificationPolicy

    private suspend fun handleEvent(event: ServerToClientEvent) {
        if (event is ServerToClientEvent.UsUpdated) {
            // The repository writes the new dates to Room on its own collector; give it a beat first.
            kotlinx.coroutines.delay(600)
            UsWidgetProvider.refreshAll(applicationContext)
        }
        // While the app is on screen it shows everything itself; notifications are for the background only.
        if (appInForeground) return
        // Settings Phase 1D: master and per-type switches.
        val type = when (event) {
            is ServerToClientEvent.MessageCreated -> NotificationType.MESSAGES
            is ServerToClientEvent.MissYou, is ServerToClientEvent.AffectionReceived -> NotificationType.AFFECTION
            is ServerToClientEvent.LetterReceived -> NotificationType.LETTERS
            else -> null
        }
        if (type != null && !notificationPolicy.shouldPost(type)) return
        when (event) {
            is ServerToClientEvent.MessageCreated -> {
                val m = event.message
                val conversation = conversationDao.getById(m.conversationId)
                if (conversation?.isMuted == true || notificationPolicy.isTimedMuted(m.conversationId)) return
                val sender = conversation?.title ?: conversation?.otherUserDisplayName ?: conversation?.otherUserPhoneE164
                    ?: getString(R.string.app_name)
                val preview = when (m.kind.uppercase()) {
                    "TEXT" -> com.omidgame.mench.core.crypto.E2eeManager.decryptBody(m.body).orEmpty()
                    "IMAGE" -> getString(R.string.notif_kind_image)
                    "VIDEO" -> getString(R.string.notif_kind_video)
                    "AUDIO" -> getString(R.string.notif_kind_voice)
                    else -> getString(R.string.notif_kind_file)
                }
                notifyPrivacyAware(m.conversationId, sender, preview, m.sequence)
            }
            is ServerToClientEvent.MissYou -> notifySimple(event.conversationId, getString(R.string.notif_miss_you))
            is ServerToClientEvent.AffectionReceived -> notifySimple(
                event.conversationId,
                getString(if (event.kind == "hug") R.string.notif_hug else R.string.notif_kiss),
            )
            is ServerToClientEvent.LetterReceived -> notifySimple(event.conversationId, getString(R.string.notif_letter))
            else -> Unit
        }
    }

    private suspend fun resolvePrivacyMode(): String {
        privacyMode?.let { return it }
        // Safest default if the server can't be reached: reveal nothing.
        return runCatching { chatApi.getNotificationPrivacy().mode }.getOrDefault("hide_content")
            .also { privacyMode = it }
    }

    /** Honours the user's notification-privacy setting (full_content / sender_only / hide_content). */
    private suspend fun notifyPrivacyAware(conversationId: String, sender: String, preview: String, sequence: Long? = null) {
        when (resolvePrivacyMode()) {
            "full_content" -> show(conversationId, sender, preview, withReply = true, sequence = sequence)
            "sender_only" -> show(conversationId, sender, getString(R.string.notif_new_message), withReply = true, sequence = sequence)
            else -> show(conversationId, getString(R.string.app_name), getString(R.string.notif_new_message))
        }
    }

    /** Fixed, content-free texts (hug, kiss, letter, miss-you): shown with the sender/conversation name unless hidden. */
    private suspend fun notifySimple(conversationId: String, text: String) {
        val conversation = conversationDao.getById(conversationId)
        if (conversation?.isMuted == true || notificationPolicy.isTimedMuted(conversationId)) return
        val name = conversation?.otherUserDisplayName ?: conversation?.otherUserPhoneE164 ?: getString(R.string.app_name)
        if (resolvePrivacyMode() == "hide_content") {
            show(conversationId, getString(R.string.app_name), getString(R.string.notif_new_message))
        } else {
            show(conversationId, name, text)
        }
    }

    private fun show(
        conversationId: String,
        title: String,
        body: String,
        withReply: Boolean = false,
        sequence: Long? = null,
        category: NotificationCategory = NotificationCategory.MESSAGES,
    ) {
        val tap = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
        }
        val requestCode = conversationId.hashCode()
        val pending = PendingIntent.getActivity(
            this, requestCode, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channelId = notificationPolicy.channelForChat(conversationId, category)
        val silence = notificationPolicy.shouldSilence(conversationId)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(if (silence) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            // Quiet hours / silent chats: still shown, never makes a sound or buzz (pre-O devices; O+ uses the channel).
            .setSilent(silence)
            .setGroup(conversationId)
            .apply {
                if (withReply) addAction(replyAction(conversationId, requestCode))
                if (sequence != null) addAction(markReadAction(conversationId, sequence, requestCode))
            }
            .build()
        try {
            NotificationManagerCompat.from(this).notify(requestCode, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted: expected and non-fatal.
        }
    }

    private fun markReadAction(conversationId: String, sequence: Long, notificationId: Int): NotificationCompat.Action {
        val intent = Intent(this, MarkReadReceiver::class.java).apply {
            putExtra(MarkReadReceiver.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(MarkReadReceiver.EXTRA_SEQUENCE, sequence)
            putExtra(MarkReadReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pending = PendingIntent.getBroadcast(
            this, notificationId + 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(R.mipmap.ic_launcher, getString(R.string.notif_mark_read), pending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()
    }

    /** The inline reply box. PendingIntent must be MUTABLE so the system can attach the typed text. */
    private fun replyAction(conversationId: String, notificationId: Int): NotificationCompat.Action {
        val intent = Intent(this, QuickReplyReceiver::class.java).apply {
            putExtra(QuickReplyReceiver.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(QuickReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(this, notificationId, intent, flags)
        val remoteInput = androidx.core.app.RemoteInput.Builder(QuickReplyReceiver.KEY_REPLY_TEXT)
            .setLabel(getString(R.string.notif_reply))
            .build()
        return NotificationCompat.Action.Builder(R.mipmap.ic_launcher, getString(R.string.notif_reply), pending)
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun buildOngoingNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_connected))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(tap)
            .build()
    }

    private fun ensureChannels() {
        notificationPolicy.ensureCategoryChannels()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ONGOING_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ONGOING_CHANNEL_ID, getString(R.string.notif_channel_connection), NotificationManager.IMPORTANCE_MIN),
            )
        }
        if (manager.getNotificationChannel(MenchFirebaseMessagingService.CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    MenchFirebaseMessagingService.CHANNEL_ID,
                    getString(R.string.notification_channel_messages),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }

    companion object {
        private const val ONGOING_ID = 7001
        private const val ONGOING_CHANNEL_ID = "connection"
    }
}
