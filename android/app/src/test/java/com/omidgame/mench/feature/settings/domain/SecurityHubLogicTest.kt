package com.omidgame.mench.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecurityHubLogicTest {
    private val day = 24L * 60 * 60 * 1000
    private fun row(id: String, group: Boolean = false, at: Long? = 0, secure: Boolean = false) =
        ChatSecurityRow(id, id, group, at, secure, locked = false)

    @Test fun `main chat is the latest direct chat and ignores groups`() {
        val rows = listOf(row("old", at = 1), row("new", at = 9), row("g", group = true, at = 99))
        assertThat(mainDirectChat(rows)?.conversationId).isEqualTo("new")
    }

    @Test fun `e2ee done depends on the main chat`() {
        assertThat(mainChatE2eeDone(listOf(row("a", at = 5, secure = true), row("b", at = 1)))).isTrue()
        assertThat(mainChatE2eeDone(listOf(row("a", at = 5), row("b", at = 1, secure = true)))).isFalse()
    }

    @Test fun `no direct chat means nothing to protect`() {
        assertThat(mainChatE2eeDone(emptyList())).isTrue()
        assertThat(mainChatE2eeDone(listOf(row("g", group = true)))).isTrue()
    }

    @Test fun `reminder never is never due`() {
        assertThat(BackupReminderPolicy.isReminderDue(null, BackupReminder.NEVER, 100 * day)).isFalse()
    }

    @Test fun `weekly reminder is due after seven days or with no backup`() {
        val now = 100 * day
        assertThat(BackupReminderPolicy.isReminderDue(now - 6 * day, BackupReminder.WEEKLY, now)).isFalse()
        assertThat(BackupReminderPolicy.isReminderDue(now - 7 * day, BackupReminder.WEEKLY, now)).isTrue()
        assertThat(BackupReminderPolicy.isReminderDue(null, BackupReminder.WEEKLY, now)).isTrue()
    }

    @Test fun `monthly reminder uses thirty days`() {
        val now = 100 * day
        assertThat(BackupReminderPolicy.isReminderDue(now - 29 * day, BackupReminder.MONTHLY, now)).isFalse()
        assertThat(BackupReminderPolicy.isReminderDue(now - 30 * day, BackupReminder.MONTHLY, now)).isTrue()
    }

    @Test fun `no backup banner needs encrypted chats and no backup ever`() {
        assertThat(BackupReminderPolicy.showNoBackupBanner(null, true)).isTrue()
        assertThat(BackupReminderPolicy.showNoBackupBanner(null, false)).isFalse()
        assertThat(BackupReminderPolicy.showNoBackupBanner(5L, true)).isFalse()
    }

    @Test fun `reminder storage round trip with safe default`() {
        BackupReminder.entries.forEach { assertThat(BackupReminder.fromStorage(it.storageValue)).isEqualTo(it) }
        assertThat(BackupReminder.fromStorage("junk")).isEqualTo(BackupReminder.NEVER)
        assertThat(BackupReminder.fromStorage(null)).isEqualTo(BackupReminder.NEVER)
    }
}
