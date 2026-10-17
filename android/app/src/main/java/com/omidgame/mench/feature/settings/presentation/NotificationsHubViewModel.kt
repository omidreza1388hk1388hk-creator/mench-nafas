package com.omidgame.mench.feature.settings.presentation

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.R
import com.omidgame.mench.core.notifications.NotificationCategory
import com.omidgame.mench.core.notifications.NotificationPolicy
import com.omidgame.mench.core.notifications.RealtimeForegroundService
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.settings.domain.ChatNotificationOverride
import com.omidgame.mench.feature.settings.domain.NotificationType
import com.omidgame.mench.feature.settings.domain.PhoneVendor
import com.omidgame.mench.feature.settings.domain.VendorGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OverrideRow(val name: String, val override: ChatNotificationOverride)

data class NotificationsHubUiState(
    val masterEnabled: Boolean = true,
    val typeEnabled: Map<NotificationType, Boolean> = NotificationType.entries.associateWith { true },
    val allowCallsInQuiet: Boolean = true,
    val overrides: List<OverrideRow> = emptyList(),
    /** System-level state, re-read whenever the screen resumes. */
    val systemNotificationsEnabled: Boolean = true,
    /** null = not applicable on this Android version. */
    val fullScreenIntentAllowed: Boolean? = null,
    val serviceRunning: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val vendor: PhoneVendor = PhoneVendor.OTHER,
)

@HiltViewModel
class NotificationsHubViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policy: NotificationPolicy,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsHubUiState())
    val state: StateFlow<NotificationsHubUiState> = _state

    init {
        // Channels must exist before the system settings page for them can be opened.
        policy.ensureCategoryChannels()
        refresh()
    }

    @Suppress("DEPRECATION") // getRunningServices still reports this app's own services, which is all we ask for.
    fun refresh() {
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) {
                runCatching { chatRepository.observeConversations().first() }.getOrDefault(emptyList())
                    .associate { it.id to (it.title ?: it.otherUserDisplayName ?: it.otherUserPhoneE164.orEmpty()) }
            }
            val running = runCatching {
                context.getSystemService(ActivityManager::class.java)
                    .getRunningServices(Int.MAX_VALUE)
                    .any { it.service.className == RealtimeForegroundService::class.java.name }
            }.getOrDefault(false)
            val battery = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            val fullScreen = if (Build.VERSION.SDK_INT >= 34) {
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            } else {
                null
            }
            _state.update {
                it.copy(
                    masterEnabled = policy.masterEnabled,
                    typeEnabled = NotificationType.entries.associateWith { t -> policy.isTypeEnabled(t) },
                    allowCallsInQuiet = policy.allowCallsInQuietHours,
                    overrides = policy.chatOverrides().map { o -> OverrideRow(names[o.conversationId].orEmpty(), o) },
                    systemNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    fullScreenIntentAllowed = fullScreen,
                    serviceRunning = running,
                    batteryUnrestricted = battery,
                    vendor = VendorGuide.detect(Build.MANUFACTURER, Build.BRAND),
                )
            }
        }
    }

    fun setMaster(v: Boolean) { policy.masterEnabled = v; refresh() }
    fun setType(t: NotificationType, v: Boolean) { policy.setTypeEnabled(t, v); refresh() }
    fun setAllowCallsInQuiet(v: Boolean) { policy.allowCallsInQuietHours = v; refresh() }
    fun clearOverride(id: String) { policy.clearChatOverrides(id); refresh() }
    fun clearAllOverrides() { policy.clearAllChatOverrides(); refresh() }

    /** Posts on the real messages channel so the test exercises the same path (channel, sound, importance). */
    fun sendTest() {
        val n = NotificationCompat.Builder(context, NotificationCategory.MESSAGES.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.sx_n_test_title))
            .setContentText(context.getString(R.string.sx_n_test_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted: the permission row on this screen explains and fixes that.
        }
    }

    private companion object { const val TEST_NOTIFICATION_ID = 7999 }
}
