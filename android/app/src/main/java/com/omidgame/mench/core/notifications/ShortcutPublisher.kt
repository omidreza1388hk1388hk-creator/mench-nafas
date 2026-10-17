package com.omidgame.mench.core.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.omidgame.mench.MainActivity
import com.omidgame.mench.R
import com.omidgame.mench.feature.chat.domain.Conversation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes launcher shortcuts and Direct Share targets for the most recent chats. Shortcut id ==
 * conversation id, so the share sheet can hand it back to us (EXTRA_SHORTCUT_ID). Titles are the
 * chat names; nothing about message content is exposed.
 */
@Singleton
class ShortcutPublisher @Inject constructor(@ApplicationContext private val context: Context) {

    fun publish(conversations: List<Conversation>) {
        val top = conversations.filter { !it.isArchived }.sortedByDescending { it.lastMessageAtEpochMillis ?: 0L }.take(4)
        val shortcuts = top.map { c ->
            val label = c.title ?: c.otherUserDisplayName ?: c.otherUserPhoneE164 ?: context.getString(R.string.app_name)
            ShortcutInfoCompat.Builder(context, c.id)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setCategories(setOf(SHARE_CATEGORY))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, Uri.parse("mench://chat/${c.id}"), context, MainActivity::class.java),
                )
                .setLongLived(true)
                .build()
        }
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    private companion object {
        const val SHARE_CATEGORY = "com.omidgame.mench.category.SHARE_TARGET"
    }
}
