package com.omidgame.mench.feature.settings.domain

/** One chat as the Security hub sees it. Built from the conversation list plus the secure/locked prefs. */
data class ChatSecurityRow(
    val conversationId: String,
    val name: String,
    val isGroup: Boolean,
    val lastMessageAt: Long?,
    val secure: Boolean,
    val locked: Boolean,
    val keyChangePending: Boolean = false,
    val peerUserId: String? = null,
)

/** The "main chat" of a couple app: the most recently active direct chat. Null when there is no direct chat. */
fun mainDirectChat(rows: List<ChatSecurityRow>): ChatSecurityRow? =
    rows.filter { !it.isGroup }.maxByOrNull { it.lastMessageAt ?: Long.MIN_VALUE }

/** E2EE for the main chat counts as done when it is on, or when there is no direct chat to protect. */
fun mainChatE2eeDone(rows: List<ChatSecurityRow>): Boolean = mainDirectChat(rows)?.secure ?: true

enum class BackupReminder(val storageValue: String, val intervalDays: Int?) {
    NEVER("never", null),
    WEEKLY("weekly", 7),
    MONTHLY("monthly", 30),
    ;

    companion object {
        fun fromStorage(v: String?): BackupReminder = entries.firstOrNull { it.storageValue == v } ?: NEVER
    }
}

object BackupReminderPolicy {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** A reminder is due when one is set and the last backup is older than the interval (or there is none). */
    fun isReminderDue(lastBackupAt: Long?, reminder: BackupReminder, now: Long): Boolean {
        val days = reminder.intervalDays ?: return false
        return lastBackupAt == null || now - lastBackupAt >= days * DAY_MS
    }

    /** The non-blocking "no backup yet" banner: only when encrypted chats exist and no backup was ever made. */
    fun showNoBackupBanner(lastBackupAt: Long?, anySecureChat: Boolean): Boolean = anySecureChat && lastBackupAt == null
}
