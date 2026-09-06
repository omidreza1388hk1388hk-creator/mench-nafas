package com.omidgame.mench.feature.calls.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.omidgame.mench.MainActivity
import com.omidgame.mench.R
import com.omidgame.mench.feature.calls.domain.CallType
import dagger.hilt.android.AndroidEntryPoint

/**
 * Owns exactly one notification for the lifetime of a call: ringing (with
 * Accept/Decline), then active (with Hang up + duration via
 * setUsesChronometer). There's deliberately no attempt here to wake the
 * app from a killed process for an incoming call — that needs a push
 * channel (FCM), which is Phase 7 scope (see README's "Known
 * limitations"); this service only keeps a call alive and visible while
 * the process itself is already running, backgrounded or not.
 */
@AndroidEntryPoint
class CallForegroundService : Service() {

    private var ringtone: Ringtone? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ACTION_START_RINGING -> {
                val callType = CallType.valueOf(intent.getStringExtra(EXTRA_CALL_TYPE) ?: CallType.VOICE.name)
                val isOutgoing = intent.getBooleanExtra(EXTRA_IS_OUTGOING, false)
                startForegroundWithType(buildRingingNotification(callType, isOutgoing), callType)
                if (!isOutgoing) startRingtone()
            }
            ACTION_START_ACTIVE -> {
                stopRingtone()
                val callType = CallType.valueOf(intent.getStringExtra(EXTRA_CALL_TYPE) ?: CallType.VOICE.name)
                startForegroundWithType(buildActiveNotification(callType), callType)
            }
            ACTION_STOP -> {
                stopRingtone()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }

    private fun startForegroundWithType(notification: Notification, callType: CallType) {
        ensureChannel()
        val type = if (callType == CallType.VIDEO) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildRingingNotification(callType: CallType, isOutgoing: Boolean): Notification {
        val contentText = if (isOutgoing) {
            getString(if (callType == CallType.VIDEO) R.string.call_outgoing_video else R.string.call_outgoing_voice)
        } else {
            getString(if (callType == CallType.VIDEO) R.string.call_incoming_video else R.string.call_incoming_voice)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .setFullScreenIntent(openAppPendingIntent(), !isOutgoing)

        if (!isOutgoing) {
            builder.addAction(0, getString(R.string.call_action_decline), actionPendingIntent(CallActionReceiver.ACTION_DECLINE))
            builder.addAction(0, getString(R.string.call_action_accept), actionPendingIntent(CallActionReceiver.ACTION_ACCEPT))
        } else {
            builder.addAction(0, getString(R.string.call_action_hangup), actionPendingIntent(CallActionReceiver.ACTION_HANGUP))
        }
        return builder.build()
    }

    private fun buildActiveNotification(callType: CallType): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(if (callType == CallType.VIDEO) R.string.call_active_video else R.string.call_active_voice))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setContentIntent(openAppPendingIntent())
            .addAction(0, getString(R.string.call_action_hangup), actionPendingIntent(CallActionReceiver.ACTION_HANGUP))
            .build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, CallActionReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun startRingtone() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            play()
        }
    }

    private fun stopRingtone() {
        ringtone?.takeIf { it.isPlaying }?.stop()
        ringtone = null
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.call_notification_channel), NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "calls"
        private const val NOTIFICATION_ID = 4201

        private const val ACTION_START_RINGING = "com.omidgame.mench.calls.START_RINGING"
        private const val ACTION_START_ACTIVE = "com.omidgame.mench.calls.START_ACTIVE"
        private const val ACTION_STOP = "com.omidgame.mench.calls.STOP"
        private const val EXTRA_CALL_TYPE = "callType"
        private const val EXTRA_IS_OUTGOING = "isOutgoing"

        fun startRinging(context: Context, callType: CallType, isOutgoing: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_RINGING
                putExtra(EXTRA_CALL_TYPE, callType.name)
                putExtra(EXTRA_IS_OUTGOING, isOutgoing)
            }
            context.startForegroundService(intent)
        }

        fun startActive(context: Context, callType: CallType) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_ACTIVE
                putExtra(EXTRA_CALL_TYPE, callType.name)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CallForegroundService::class.java).apply { action = ACTION_STOP })
        }
    }
}
