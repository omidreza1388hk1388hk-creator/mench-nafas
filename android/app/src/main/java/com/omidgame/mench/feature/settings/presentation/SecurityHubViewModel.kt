package com.omidgame.mench.feature.settings.presentation

import android.app.KeyguardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.backup.BackupStatusStore
import com.omidgame.mench.core.crypto.E2eeManager
import com.omidgame.mench.core.crypto.PeerKeyTrust
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.chat.presentation.ChatLock
import com.omidgame.mench.feature.security.domain.AppLockRepository
import com.omidgame.mench.feature.settings.domain.BackupReminder
import com.omidgame.mench.feature.settings.domain.BackupReminderPolicy
import com.omidgame.mench.feature.settings.domain.ChatSecurityRow
import com.omidgame.mench.feature.settings.domain.SecurityCheckResult
import com.omidgame.mench.feature.settings.domain.SecurityCheckup
import com.omidgame.mench.feature.settings.domain.SecurityFacts
import com.omidgame.mench.feature.settings.domain.mainChatE2eeDone
import com.omidgame.mench.feature.settings.domain.mainDirectChat
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

data class SecurityHubUiState(
    val loading: Boolean = true,
    val chats: List<ChatSecurityRow> = emptyList(),
    val checkup: SecurityCheckResult = SecurityCheckup.evaluate(SecurityFacts(true, true, true, true, true, true)),
    val lastBackupAt: Long? = null,
    val reminder: BackupReminder = BackupReminder.NEVER,
    val showNoBackupBanner: Boolean = false,
    val reminderDue: Boolean = false,
    /** Chat whose encryption switch is being changed right now, and the one whose last change failed. */
    val busyChatId: String? = null,
    val failedChatId: String? = null,
) {
    val secureChats: List<ChatSecurityRow> get() = chats.filter { it.secure }
    val lockedChats: List<ChatSecurityRow> get() = chats.filter { it.locked }
}

@HiltViewModel
class SecurityHubViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val appLockRepository: AppLockRepository,
    private val chatApi: ChatApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SecurityHubUiState())
    val state: StateFlow<SecurityHubUiState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { loadRows() }
            val lockOn = appLockRepository.isAppLockEnabled()
            val screenLock = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: true
            // Offline/unknown two-step status is NOT reported as a problem (we only flag what we know is missing).
            val twoStep = withContext(Dispatchers.IO) { runCatching { chatApi.getTwoStep().enabled }.getOrNull() } ?: true
            val last = BackupStatusStore.lastBackupAt(context)
            val reminder = BackupStatusStore.reminder(context)
            val now = System.currentTimeMillis()
            val anySecure = rows.any { it.secure }
            val facts = SecurityFacts(
                deviceScreenLockSet = screenLock,
                appLockEnabled = lockOn,
                twoStepEnabled = twoStep,
                backupExists = last != null,
                devicesReviewed = withContext(Dispatchers.IO) { SecurityFlags.devicesReviewed(context) },
                mainChatE2eeEnabled = mainChatE2eeDone(rows),
                anyE2eeChat = anySecure,
            )
            _state.update {
                it.copy(
                    loading = false,
                    chats = rows,
                    checkup = SecurityCheckup.evaluate(facts),
                    lastBackupAt = last,
                    reminder = reminder,
                    showNoBackupBanner = BackupReminderPolicy.showNoBackupBanner(last, anySecure),
                    reminderDue = BackupReminderPolicy.isReminderDue(last, reminder, now),
                )
            }
        }
    }

    private suspend fun loadRows(): List<ChatSecurityRow> =
        chatRepository.observeConversations().first().map { c ->
            ChatSecurityRow(
                conversationId = c.id,
                name = c.title ?: c.otherUserDisplayName ?: c.otherUserPhoneE164.orEmpty(),
                isGroup = c.kind == "group",
                lastMessageAt = c.lastMessageAtEpochMillis,
                secure = E2eeManager.isSecureChat(c.id),
                locked = ChatLock.isLocked(context, c.id),
                keyChangePending = E2eeManager.peerOf(c.id)?.let { PeerKeyTrust.hasPendingChange(context, it) } == true,
                peerUserId = c.otherUserId,
            )
        }

    fun setReminder(r: BackupReminder) {
        BackupStatusStore.setReminder(context, r)
        refresh()
    }

    fun unlockChat(row: ChatSecurityRow) {
        ChatLock.set(context, row.conversationId, false)
        refresh()
    }

    fun acknowledgeKeyChange(row: ChatSecurityRow) {
        row.peerUserId?.let { PeerKeyTrust.acknowledge(context, it) }
        refresh()
    }

    fun markDevicesReviewed() {
        SecurityFlags.markDevicesReviewed(context)
    }

    /** Turns encryption off/on for one chat using the same repository call the chat's own security dialog uses. */
    fun setChatSecure(row: ChatSecurityRow, enabled: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(busyChatId = row.conversationId, failedChatId = null) }
            val ok = chatRepository.setSecureChat(row.conversationId, row.peerUserId, enabled)
            _state.update { it.copy(busyChatId = null, failedChatId = if (ok) null else row.conversationId) }
            refresh()
        }
    }

    /** Checkup fix: encrypt the main direct chat. */
    fun enableMainChatE2ee() {
        val main = mainDirectChat(_state.value.chats) ?: return
        if (!main.secure) setChatSecure(main, true)
    }
}

/** Small local flags the checkup needs. Not secret, not synced. */
object SecurityFlags {
    private const val PREFS = "mench_security_flags"
    private const val DEVICES = "devices_reviewed_at"
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun devicesReviewed(c: Context): Boolean = p(c).getLong(DEVICES, 0L) > 0
    fun markDevicesReviewed(c: Context) { p(c).edit().putLong(DEVICES, System.currentTimeMillis()).apply() }
}
