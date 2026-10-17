package com.omidgame.mench.feature.chat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.omidgame.mench.R
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.chat.presentation.LatLon
import com.omidgame.mench.feature.chat.presentation.LocationFetcher
import com.omidgame.mench.feature.chat.presentation.RichBody
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LiveLocationEntryPoint { fun chatRepository(): ChatRepository }

/**
 * Phase 2A live location. Sends one location message that carries an expiry, then keeps editing that same message
 * with a fresh fix every [UPDATE_INTERVAL_MS] until the expiry or until the user taps Stop (which edits the
 * message once more with an expiry of "now" so recipients see it end immediately). A foreground service of type
 * location keeps it alive in the background; the notification always shows a Stop action.
 */
class LiveLocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishSharing()
            return START_NOT_STICKY
        }
        val conversationId = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
        val until = intent?.getLongExtra(EXTRA_UNTIL, 0L) ?: 0L
        if (conversationId == null || until <= System.currentTimeMillis() || job?.isActive == true) {
            if (job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        running = true
        val first = if (intent.hasExtra(EXTRA_LAT)) LatLon(intent.getDoubleExtra(EXTRA_LAT, 0.0), intent.getDoubleExtra(EXTRA_LON, 0.0)) else null
        job = scope.launch { run(conversationId, until, first) }
        return START_NOT_STICKY
    }

    private suspend fun run(conversationId: String, until: Long, firstFix: LatLon?) {
        val repo = EntryPointAccessors.fromApplication(applicationContext, LiveLocationEntryPoint::class.java).chatRepository()
        val firstKnown = firstFix ?: LocationFetcher.current(applicationContext)
        if (firstKnown == null) { finishSharing(); return }
        // Non-null from here on; reassigned (never nulled) by each fresh fix below.
        var last: LatLon = firstKnown
        val startBody = RichBody.encodeLiveLocation(last.latitude, last.longitude, until)
        val clientId = startBody?.let { repo.startLiveLocation(conversationId, it) }
        if (clientId == null) { finishSharing(); return }
        // The edit needs the server id, which exists once the Outbox delivered the first message.
        var serverId: String? = null
        var waited = 0L
        while (serverId == null && waited < SERVER_ID_TIMEOUT_MS) {
            serverId = repo.serverIdOf(clientId)
            if (serverId == null) { delay(2_000); waited += 2_000 }
        }
        if (serverId == null) { finishSharing(); return }
        // Remembered so an early Stop (from the notification) can still end the shared message.
        pendingEnd = PendingEnd(conversationId, serverId, last)
        while (System.currentTimeMillis() < until) {
            delay(UPDATE_INTERVAL_MS)
            val fix = LocationFetcher.current(applicationContext) ?: last
            last = fix
            pendingEnd = PendingEnd(conversationId, serverId, fix)
            RichBody.encodeLiveLocation(fix.latitude, fix.longitude, until)?.let { repo.editMessage(conversationId, serverId, it) }
        }
        // Natural end: the loop edits the message itself, so finishSharing() must not edit it a second time.
        endMessage(repo, conversationId, serverId, last)
        pendingEnd = null
        finishSharing()
    }

    private suspend fun endMessage(repo: ChatRepository, conversationId: String, serverId: String, last: LatLon) {
        RichBody.encodeLiveLocation(last.latitude, last.longitude, System.currentTimeMillis())
            ?.let { repo.editMessage(conversationId, serverId, it) }
    }

    /** Stop button: ends the shared message right away, then stops the service. */
    private fun finishSharing() {
        // Cancelling the loop is not enough on its own: the message must be edited to an expired time. The loop
        // does that itself when it reaches the end; for an early Stop the stop path below edits it.
        val active = job
        job = null
        if (active != null && active.isActive) active.cancel()
        scope.launch {
            val ref = pendingEnd
            if (ref != null) {
                val repo = EntryPointAccessors.fromApplication(applicationContext, LiveLocationEntryPoint::class.java).chatRepository()
                endMessage(repo, ref.conversationId, ref.serverId, ref.last)
            }
            running = false
            ServiceCompat.stopForeground(this@LiveLocationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private data class PendingEnd(val conversationId: String, val serverId: String, val last: LatLon)

    @Volatile private var pendingEnd: PendingEnd? = null

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.live_location_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.live_location_notif_title))
            .setContentText(getString(R.string.live_location_notif_text))
            .setOngoing(true)
            .addAction(0, getString(R.string.live_location_stop), stop)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.omidgame.mench.action.STOP_LIVE_LOCATION"
        private const val EXTRA_CONVERSATION_ID = "conversation_id"
        private const val EXTRA_UNTIL = "until"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LON = "lon"
        private const val CHANNEL_ID = "live_location"
        private const val NOTIFICATION_ID = 7421
        private const val UPDATE_INTERVAL_MS = 30_000L
        private const val SERVER_ID_TIMEOUT_MS = 120_000L

        /** True while a live location is being shared; the UI uses it to refuse a second session. */
        @Volatile var running: Boolean = false
            private set

        /** Caller must already hold a location permission (the foreground service type requires it on Android 14+). */
        fun start(context: Context, conversationId: String, durationMs: Long, firstFix: LatLon?) {
            val intent = Intent(context, LiveLocationService::class.java)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId)
                .putExtra(EXTRA_UNTIL, System.currentTimeMillis() + durationMs)
            if (firstFix != null) intent.putExtra(EXTRA_LAT, firstFix.latitude).putExtra(EXTRA_LON, firstFix.longitude)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
