package com.omidgame.mench.feature.product

import android.content.Intent
import android.net.Uri
import java.util.concurrent.atomic.AtomicReference

/** What another app asked MENCH to send (text and/or one image/file), waiting for the user to pick a chat. */
data class ShareDraft(
    val text: String?,
    val streamUri: String?,
    val mimeType: String?,
    /** Set when the user tapped a specific chat in the system share sheet (Direct Share). */
    val preselectedConversationId: String?,
)

object ShareInbox {
    private val ref = AtomicReference<ShareDraft?>(null)
    fun set(d: ShareDraft) = ref.set(d)
    fun peek(): ShareDraft? = ref.get()
    fun clear() = ref.set(null)
}

sealed interface DeepLinkTarget {
    data class Route(val route: String) : DeepLinkTarget
    data class Chat(val conversationId: String) : DeepLinkTarget
}

/**
 * Turns an incoming Intent (mench:// link, https://mench.app/i/CODE app link, or ACTION_SEND from
 * another app) into an in-app destination. Pure function of the Intent, so it is easy to unit test.
 */
object DeepLinks {
    const val SHARE_ROUTE = "share-picker"
    const val NEW_CHAT_ROUTE = "conversations/new"

    fun resolve(intent: Intent?): DeepLinkTarget? {
        intent ?: return null
        if (intent.action == Intent.ACTION_SEND) return resolveShare(intent)
        val data: Uri = intent.data ?: return null
        return when {
            data.scheme == "mench" -> when (data.host) {
                "invite" -> data.lastPathSegment?.let(InviteLinks::extractCode)?.let { DeepLinkTarget.Route(ProductRoutes.findPeople(it)) }
                "chat" -> data.lastPathSegment?.takeIf { it.length in 8..64 }?.let { DeepLinkTarget.Chat(it) }
                "new" -> DeepLinkTarget.Route(NEW_CHAT_ROUTE)
                "find" -> DeepLinkTarget.Route(ProductRoutes.findPeople())
                "link" -> DeepLinkTarget.Route("link-device?payload=" + Uri.encode(data.toString()))
                else -> null
            }
            data.host?.endsWith("mench.app") == true && data.pathSegments.firstOrNull() == "i" ->
                data.pathSegments.getOrNull(1)?.let(InviteLinks::extractCode)?.let { DeepLinkTarget.Route(ProductRoutes.findPeople(it)) }
            else -> null
        }
    }

    private fun resolveShare(intent: Intent): DeepLinkTarget? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (text.isNullOrBlank() && stream == null) return null
        ShareInbox.set(ShareDraft(text, stream?.toString(), intent.type, intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)))
        return DeepLinkTarget.Route(SHARE_ROUTE)
    }
}
