package com.omidgame.mench.core.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.omidgame.mench.MainActivity
import com.omidgame.mench.R
import com.omidgame.mench.core.network.PlannerItem
import java.time.Instant

/**
 * Local alarms for reminders / tasks / events. The server owns the data; the phone just rings at the
 * right time, so it works offline. Uses inexact-while-idle alarms (no SCHEDULE_EXACT_ALARM permission
 * needed): they can be a few minutes late in deep Doze, never early. Alarms are re-registered every
 * time the Reminders screen loads or the app opens; they do NOT survive a reboot until the next open.
 */
object ReminderScheduler {
    private const val CHANNEL_ID = "reminders"

    fun schedule(context: Context, items: List<PlannerItem>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        items.forEach { item ->
            val due = item.dueAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            val pi = pending(context, item.id, item.title, item.conversationId)
            if (item.completedAt != null || due == null) { am.cancel(pi); return@forEach }
            val at = due - item.remindMinutesBefore * 60_000L
            if (at > now) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.cancel(pi)
        }
    }

    fun cancel(context: Context, id: String) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context, id, "", null))
    }

    private fun pending(context: Context, id: String, title: String, conversationId: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.omidgame.mench.REMINDER.$id"
            putExtra("id", id); putExtra("title", title); putExtra("conversationId", conversationId)
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun ensureChannel(context: Context) {
        val m = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (m.getNotificationChannel(CHANNEL_ID) == null) {
            m.createNotificationChannel(
                android.app.NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    internal fun show(context: Context, id: String, title: String, conversationId: String?, silent: Boolean) {
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            conversationId?.let { putExtra(MainActivity.EXTRA_CONVERSATION_ID, it) }
        }
        val tap = PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.reminders_notification_title))
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setSilent(silent)
            .build()
        try { NotificationManagerCompat.from(context).notify(("rem" + id).hashCode(), n) } catch (_: SecurityException) { }
    }
}

/** Fires a due reminder, honouring the user's quiet hours (silent but still shown). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val title = intent.getStringExtra("title").orEmpty()
        val policy = NotificationPolicy(context.applicationContext)
        if (!policy.shouldPost(com.omidgame.mench.feature.settings.domain.NotificationType.REMINDERS)) return
        val silent = policy.isQuietNow()
        ReminderScheduler.show(context, id, title, intent.getStringExtra("conversationId"), silent)
    }
}
