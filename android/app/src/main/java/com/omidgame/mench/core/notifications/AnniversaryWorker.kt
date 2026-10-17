package com.omidgame.mench.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omidgame.mench.MainActivity
import com.omidgame.mench.R
import com.omidgame.mench.core.database.ConversationDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Once a day (around 9:00 local) checks the shared dates cached on the direct
 * conversation and posts a warm local notification for: the yearly and
 * monthly anniversary of getting together, round day-counts (100, 200, 365,
 * 500, 1000…), and the day before / day of your next meeting. Entirely
 * on-device — no server, works offline. Remembers what it already announced
 * so a re-run never repeats a notification.
 */
class AnniversaryWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun conversationDao(): ConversationDao
    }

    override suspend fun doWork(): Result {
        val dao = EntryPointAccessors.fromApplication(context.applicationContext, WorkerEntryPoint::class.java).conversationDao()
        val today = LocalDate.now()
        val prefs = context.getSharedPreferences("anniversary", Context.MODE_PRIVATE)
        runCatching { dao.getAllOnce() }.getOrDefault(emptyList())
            .filter { it.kind != "group" }
            .forEach { conversation ->
                val messages = buildList {
                    conversation.togetherSince?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }?.let { since ->
                        val days = ChronoUnit.DAYS.between(since, today)
                        val years = ChronoUnit.YEARS.between(since, today)
                        val months = ChronoUnit.MONTHS.between(since, today)
                        // Day-of-month clamped to the month's length (a 31st anniversary lands on the 30th/28th).
                        val anniversaryDay = minOf(since.dayOfMonth, YearMonth.from(today).lengthOfMonth())
                        when {
                            years > 0 && since.monthValue == today.monthValue && anniversaryDay == today.dayOfMonth ->
                                add(context.getString(R.string.anniv_years, years.toInt()))
                            months > 0 && anniversaryDay == today.dayOfMonth ->
                                add(context.getString(R.string.anniv_months, months.toInt()))
                        }
                        if (days in MILESTONE_DAYS) add(context.getString(R.string.anniv_days, days.toInt()))
                    }
                    conversation.nextMeetDate?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }?.let { meet ->
                        when (ChronoUnit.DAYS.between(today, meet)) {
                            1L -> add(context.getString(R.string.anniv_meet_tomorrow))
                            0L -> add(context.getString(R.string.anniv_meet_today))
                        }
                    }
                }
                messages.forEachIndexed { index, text ->
                    val key = "${conversation.id}:$today:$index"
                    if (!prefs.getBoolean(key, false)) {
                        prefs.edit().putBoolean(key, true).apply()
                        post(conversation.id, text, conversation.id.hashCode() + index + 100)
                    }
                }
            }
        return Result.success()
    }

    private fun post(conversationId: String, text: String, notificationId: Int) {
        // Settings Phase 1D: master and "reminders & anniversaries" switches.
        if (!NotificationPolicy(context.applicationContext).shouldPost(com.omidgame.mench.feature.settings.domain.NotificationType.REMINDERS)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.anniv_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val tap = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Notifications not allowed: nothing to do.
        }
    }

    companion object {
        private const val CHANNEL_ID = "anniversaries"
        private const val WORK_NAME = "anniversary_daily"
        private val MILESTONE_DAYS = setOf(100L, 200L, 300L, 365L, 500L, 666L, 777L, 1000L, 1500L, 2000L)

        /** Idempotent: schedules the daily check, first run at the next 9:00 local. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var first = now.toLocalDate().atTime(9, 0)
            if (!first.isAfter(now)) first = first.plusDays(1)
            val delayMinutes = ChronoUnit.MINUTES.between(now, first)
            val request = PeriodicWorkRequestBuilder<AnniversaryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
