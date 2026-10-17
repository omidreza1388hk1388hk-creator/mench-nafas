package com.omidgame.mench.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.omidgame.mench.R
import com.omidgame.mench.feature.settings.domain.ChatNotificationOverride
import com.omidgame.mench.feature.settings.domain.ChatOverrides
import com.omidgame.mench.feature.settings.domain.NotificationGate
import com.omidgame.mench.feature.settings.domain.NotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** Notification categories, each with its own channel so the user can tune them in system settings. */
enum class NotificationCategory(val channelId: String, val nameRes: Int, val importance: Int) {
    MESSAGES("messages", R.string.notification_channel_messages, NotificationManager.IMPORTANCE_HIGH),
    COUPLE("couple", R.string.notif_channel_couple, NotificationManager.IMPORTANCE_DEFAULT),
    CALLS("calls", R.string.notif_channel_calls, NotificationManager.IMPORTANCE_HIGH),
    SYSTEM("system", R.string.notif_channel_system, NotificationManager.IMPORTANCE_LOW),
}

enum class ChatVibration(val pattern: LongArray?) {
    DEFAULT(longArrayOf(0, 250, 120, 250)),
    SHORT(longArrayOf(0, 120)),
    LONG(longArrayOf(0, 600, 150, 600)),
    OFF(null),
}

/**
 * Phase 1C notification policy: timed mutes ("1 hour / 8 hours / until tomorrow"), quiet hours
 * (Do-Not-Disturb schedule inside the app), per-chat sound + vibration, and per-category channels.
 * Plain SharedPreferences: tiny values read on the notification path, never secrets.
 */
@Singleton
class NotificationPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("mench_notification_policy", Context.MODE_PRIVATE)

    // ---- Settings Phase 1D: in-app switches (all default ON, so nothing changes until the user flips one) ----
    var masterEnabled: Boolean
        get() = prefs.getBoolean("master_enabled", true)
        set(v) { prefs.edit().putBoolean("master_enabled", v).apply() }

    fun isTypeEnabled(type: NotificationType): Boolean = prefs.getBoolean("type_${type.storageKey}", true)

    fun setTypeEnabled(type: NotificationType, enabled: Boolean) {
        prefs.edit().putBoolean("type_${type.storageKey}", enabled).apply()
    }

    /** The one question every posting site asks before building a notification. */
    fun shouldPost(type: NotificationType): Boolean = NotificationGate.isAllowed(masterEnabled, isTypeEnabled(type))

    /** Calls ring during quiet hours unless the user turned this exception off (default: partner calls still ring). */
    var allowCallsInQuietHours: Boolean
        get() = prefs.getBoolean("quiet_allow_calls", true)
        set(v) { prefs.edit().putBoolean("quiet_allow_calls", v).apply() }

    fun suppressCallRing(): Boolean = isQuietNow() && !allowCallsInQuietHours

    // ---- per-chat overrides list ----
    fun chatOverrides(): List<ChatNotificationOverride> = ChatOverrides.fromPrefs(prefs.all, System.currentTimeMillis())

    /** Removes mute/silent/vibration/sound for one chat. The channel revision is bumped so the old custom channel is not reused. */
    fun clearChatOverrides(conversationId: String) {
        val editor = prefs.edit()
        ChatOverrides.keysFor(conversationId).forEach { editor.remove(it) }
        editor.putInt("chan_rev_$conversationId", prefs.getInt("chan_rev_$conversationId", 0) + 1).apply()
    }

    fun clearAllChatOverrides() {
        chatOverrides().forEach { clearChatOverrides(it.conversationId) }
    }

    // ---- timed mute ----
    fun muteFor(conversationId: String, durationMs: Long) {
        prefs.edit().putLong("mute_until_$conversationId", System.currentTimeMillis() + durationMs).apply()
    }

    /** Mute until 08:00 the next morning (local time). */
    fun muteUntilTomorrow(conversationId: String) {
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        prefs.edit().putLong("mute_until_$conversationId", c.timeInMillis).apply()
    }

    fun clearTimedMute(conversationId: String) {
        prefs.edit().remove("mute_until_$conversationId").apply()
    }

    fun isTimedMuted(conversationId: String): Boolean =
        prefs.getLong("mute_until_$conversationId", 0L) > System.currentTimeMillis()

    // ---- quiet hours ----
    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean("quiet_enabled", false)
        set(v) { prefs.edit().putBoolean("quiet_enabled", v).apply() }

    /** Minutes after midnight. Default 23:00 -> 07:00. */
    var quietStartMinutes: Int
        get() = prefs.getInt("quiet_start", 23 * 60)
        set(v) { prefs.edit().putInt("quiet_start", v.coerceIn(0, 1439)).apply() }

    var quietEndMinutes: Int
        get() = prefs.getInt("quiet_end", 7 * 60)
        set(v) { prefs.edit().putInt("quiet_end", v.coerceIn(0, 1439)).apply() }

    /** Handles ranges that cross midnight (23:00-07:00). */
    fun isQuietNow(nowMinutes: Int = currentMinutes()): Boolean {
        if (!quietHoursEnabled) return false
        val s = quietStartMinutes
        val e = quietEndMinutes
        return if (s == e) false else if (s < e) nowMinutes in s until e else nowMinutes >= s || nowMinutes < e
    }

    private fun currentMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    // ---- per-chat sound / vibration ----
    fun chatVibration(conversationId: String): ChatVibration =
        runCatching { ChatVibration.valueOf(prefs.getString("vib_$conversationId", null) ?: "DEFAULT") }.getOrDefault(ChatVibration.DEFAULT)

    fun chatSilent(conversationId: String): Boolean = prefs.getBoolean("silent_$conversationId", false)

    fun setChatSound(conversationId: String, silent: Boolean, vibration: ChatVibration) {
        prefs.edit().putBoolean("silent_$conversationId", silent).putString("vib_$conversationId", vibration.name).apply()
        // Channel settings are immutable after creation on O+, so a changed choice needs a fresh channel id.
        prefs.edit().putInt("chan_rev_$conversationId", prefs.getInt("chan_rev_$conversationId", 0) + 1).apply()
    }

    /** Phase 1 completion: a ringtone the user picked for this chat (null = system default notification sound). */
    fun chatSoundUri(conversationId: String): String? = prefs.getString("sound_uri_$conversationId", null)

    fun setChatSoundUri(conversationId: String, uri: String?) {
        val editor = prefs.edit()
        if (uri == null) editor.remove("sound_uri_$conversationId") else editor.putString("sound_uri_$conversationId", uri)
        // Same reason as setChatSound: channel sound is immutable once created.
        editor.putInt("chan_rev_$conversationId", prefs.getInt("chan_rev_$conversationId", 0) + 1).apply()
    }

    fun hasCustomSound(conversationId: String): Boolean =
        chatSilent(conversationId) || chatVibration(conversationId) != ChatVibration.DEFAULT ||
            chatSoundUri(conversationId) != null

    /** A notification is dropped entirely only for timed/permanent mutes; quiet hours only silence it. */
    fun shouldSilence(conversationId: String): Boolean = isQuietNow() || chatSilent(conversationId)

    // ---- channels ----
    fun ensureCategoryChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationCategory.entries.forEach { cat ->
            if (manager.getNotificationChannel(cat.channelId) == null) {
                manager.createNotificationChannel(NotificationChannel(cat.channelId, context.getString(cat.nameRes), cat.importance))
            }
        }
    }

    /** Channel id to post a chat notification on; creates a per-chat channel when the user customised it. */
    fun channelForChat(conversationId: String, category: NotificationCategory = NotificationCategory.MESSAGES): String {
        ensureCategoryChannels()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !hasCustomSound(conversationId)) return category.channelId
        val rev = prefs.getInt("chan_rev_$conversationId", 0)
        val id = "chat_${conversationId}_$rev"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(id, context.getString(R.string.notif_channel_custom_chat), category.importance)
            if (chatSilent(conversationId)) {
                channel.setSound(null, null)
            } else {
                val soundUri = chatSoundUri(conversationId)?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                channel.setSound(
                    soundUri,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
                )
            }
            val pattern = chatVibration(conversationId).pattern
            channel.enableVibration(pattern != null)
            if (pattern != null) channel.vibrationPattern = pattern
            manager.createNotificationChannel(channel)
        }
        return id
    }
}
