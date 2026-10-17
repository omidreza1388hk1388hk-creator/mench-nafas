package com.omidgame.mench.core.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.omidgame.mench.MainActivity
import com.omidgame.mench.R
import com.omidgame.mench.core.database.ConversationDao
import com.omidgame.mench.feature.chat.domain.ChatRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Phase 1C leftover: Glance home-screen widgets. Two widgets share one data loader that reads the
 * cached conversation (so they work offline and never touch the network):
 *  - [CoupleCounterWidget]: days together + countdown to the next meeting.
 *  - [PartnerQuickWidget]: partner name, open-chat tap, and three one-tap quick messages.
 * Neither widget ever shows message text, so nothing private is visible on a lock screen or home screen.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GlanceWidgetEntryPoint {
    fun conversationDao(): ConversationDao
    fun chatRepository(): ChatRepository
}

internal data class PartnerSnapshot(
    val conversationId: String?,
    val name: String,
    val daysTogether: Long?,
    val daysUntilMeet: Long?,
)

private suspend fun loadPartnerSnapshot(context: Context): PartnerSnapshot {
    val dao = EntryPointAccessors.fromApplication(context.applicationContext, GlanceWidgetEntryPoint::class.java).conversationDao()
    val all = runCatching { dao.getAllOnce() }.getOrDefault(emptyList())
    // Same pick rule as the classic widget: the direct chat with shared dates, else any direct chat.
    val direct = all.firstOrNull { it.kind != "group" && it.togetherSince != null } ?: all.firstOrNull { it.kind != "group" }
    val today = LocalDate.now()
    val together = direct?.togetherSince
        ?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), today) }.getOrNull() }
        ?.takeIf { it >= 0 }
    val meet = direct?.nextMeetDate
        ?.let { runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) }.getOrNull() }
        ?.takeIf { it >= 0 }
    return PartnerSnapshot(
        conversationId = direct?.id,
        name = direct?.otherUserDisplayName ?: direct?.otherUserPhoneE164 ?: "",
        daysTogether = together,
        daysUntilMeet = meet,
    )
}

private val White = ColorProvider(Color.White)
private val SoftWhite = ColorProvider(Color(0xFFFFE3EC))

class CoupleCounterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadPartnerSnapshot(context)
        val daysText = snapshot.daysTogether?.let { context.getString(R.string.us_days_together, it) }
            ?: context.getString(R.string.widget_set_dates)
        val meetText = snapshot.daysUntilMeet?.let { days ->
            if (days == 0L) context.getString(R.string.us_meet_today) else context.getString(R.string.us_days_until_meet, days)
        }.orEmpty()
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_background))
                    .padding(14.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💗", style = TextStyle(fontSize = 22.sp, color = White))
                Text(daysText, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = White))
                if (meetText.isNotEmpty()) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(meetText, style = TextStyle(fontSize = 13.sp, color = SoftWhite))
                }
            }
        }
    }
}

class CoupleCounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CoupleCounterWidget()
}

private val ConversationIdKey = ActionParameters.Key<String>("conversation_id")
private val QuickTextKey = ActionParameters.Key<String>("quick_text")

/** Sends one canned message through the normal outbox (queued and retried like any message). */
class SendQuickMessageAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val conversationId = parameters[ConversationIdKey] ?: return
        val text = parameters[QuickTextKey] ?: return
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, GlanceWidgetEntryPoint::class.java)
                .chatRepository()
                .sendMessage(conversationId, text, null)
        }
    }
}

class PartnerQuickWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadPartnerSnapshot(context)
        val quick = listOf(
            context.getString(R.string.widget_quick_heart),
            context.getString(R.string.widget_quick_miss),
            context.getString(R.string.widget_quick_night),
        )
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_background))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = snapshot.name.ifBlank { context.getString(R.string.app_name) },
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = White),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                )
                Spacer(GlanceModifier.height(8.dp))
                if (snapshot.conversationId == null) {
                    Text(context.getString(R.string.widget_open_to_start), style = TextStyle(fontSize = 13.sp, color = SoftWhite))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        quick.forEachIndexed { index, text ->
                            if (index > 0) Spacer(GlanceModifier.width(6.dp))
                            Text(
                                text = text,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = White),
                                maxLines = 1,
                                modifier = GlanceModifier
                                    .background(ColorProvider(Color(0x33FFFFFF)))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .clickable(
                                        actionRunCallback<SendQuickMessageAction>(
                                            actionParametersOf(
                                                ConversationIdKey to snapshot.conversationId,
                                                QuickTextKey to text,
                                            ),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

class PartnerQuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PartnerQuickWidget()
}

/** Re-renders both Glance widgets (called when the shared dates change, next to the classic widget's refresh). */
suspend fun updateGlanceWidgets(context: Context) {
    runCatching { CoupleCounterWidget().updateAll(context) }
    runCatching { PartnerQuickWidget().updateAll(context) }
}
