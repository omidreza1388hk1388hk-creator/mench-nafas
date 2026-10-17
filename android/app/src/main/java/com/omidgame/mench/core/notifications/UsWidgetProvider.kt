package com.omidgame.mench.core.notifications

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.omidgame.mench.MainActivity
import com.omidgame.mench.R
import com.omidgame.mench.core.database.ConversationDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Home-screen "us" widget: days together and the countdown to the next
 * meeting, read from the cached direct conversation (so it works offline).
 * Refreshes every ~30 minutes by the system, whenever the shared dates
 * change (the background service calls [refreshAll]), and tapping it opens
 * the app. Uses plain RemoteViews — no extra library.
 */
class UsWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun conversationDao(): ConversationDao
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                    .conversationDao()
                val direct = runCatching { dao.getAllOnce() }.getOrDefault(emptyList())
                    .firstOrNull { it.kind != "group" && it.togetherSince != null }
                    ?: runCatching { dao.getAllOnce() }.getOrDefault(emptyList()).firstOrNull { it.kind != "group" }

                val daysText = direct?.togetherSince
                    ?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.now()) }.getOrNull() }
                    ?.takeIf { it >= 0 }
                    ?.let { context.getString(R.string.us_days_together, it) }
                    ?: context.getString(R.string.widget_set_dates)

                val meetText = direct?.nextMeetDate
                    ?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it)) }.getOrNull() }
                    ?.takeIf { it >= 0 }
                    ?.let { days ->
                        if (days == 0L) context.getString(R.string.us_meet_today)
                        else context.getString(R.string.us_days_until_meet, days)
                    }
                    .orEmpty()

                val open = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_us).apply {
                        setTextViewText(R.id.widget_days, daysText)
                        setTextViewText(R.id.widget_meet, meetText)
                        setOnClickPendingIntent(R.id.widget_root, open)
                    }
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Re-renders every placed instance (call after the shared dates change). */
        fun refreshAll(context: Context) {
            // Glance widgets render from the same cached data; refresh them alongside the classic one.
            CoroutineScope(Dispatchers.Default).launch {
                com.omidgame.mench.core.widgets.updateGlanceWidgets(context.applicationContext)
            }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UsWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, UsWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    },
                )
            }
        }
    }
}
