package com.omidgame.mench.core.media

import android.content.Context
import com.omidgame.mench.core.network.NetworkKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class MediaKind { PHOTO, VIDEO, FILE, VOICE }

enum class ChatDownloadOverride { DEFAULT, ALWAYS, NEVER }

/**
 * Phase 1B: auto-download rules by network type and media type, plus per-chat overrides.
 * Stored in plain SharedPreferences (not secrets, tiny, read synchronously on the UI path).
 * Defaults: everything on Wi-Fi; photos + voice on mobile data; only voice while roaming.
 */
@Singleton
class AutoDownloadSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("mench_auto_download", Context.MODE_PRIVATE)

    private fun defaultFor(network: NetworkKind, kind: MediaKind): Boolean = when (network) {
        NetworkKind.WIFI -> true
        NetworkKind.MOBILE -> kind == MediaKind.PHOTO || kind == MediaKind.VOICE
        NetworkKind.ROAMING -> kind == MediaKind.VOICE
        NetworkKind.NONE -> false
    }

    fun isEnabled(network: NetworkKind, kind: MediaKind): Boolean =
        prefs.getBoolean("rule_${network.name}_${kind.name}", defaultFor(network, kind))

    fun setEnabled(network: NetworkKind, kind: MediaKind, enabled: Boolean) {
        prefs.edit().putBoolean("rule_${network.name}_${kind.name}", enabled).apply()
    }

    /** Files larger than this never auto-download on mobile data / roaming (MB). Wi-Fi has no cap. */
    fun maxMobileSizeMb(): Int = prefs.getInt("max_mobile_mb", 10)

    fun setMaxMobileSizeMb(mb: Int) {
        prefs.edit().putInt("max_mobile_mb", mb.coerceIn(1, 200)).apply()
    }

    fun chatOverride(conversationId: String): ChatDownloadOverride =
        runCatching { ChatDownloadOverride.valueOf(prefs.getString("chat_$conversationId", null) ?: "DEFAULT") }
            .getOrDefault(ChatDownloadOverride.DEFAULT)

    fun setChatOverride(conversationId: String, override: ChatDownloadOverride) {
        prefs.edit().apply {
            if (override == ChatDownloadOverride.DEFAULT) remove("chat_$conversationId") else putString("chat_$conversationId", override.name)
        }.apply()
    }

    /** The one question the media UI asks before fetching a full-size attachment. */
    fun shouldAutoDownload(
        conversationId: String?,
        kind: MediaKind,
        sizeBytes: Long,
        network: NetworkKind,
    ): Boolean {
        if (network == NetworkKind.NONE) return false
        when (conversationId?.let(::chatOverride) ?: ChatDownloadOverride.DEFAULT) {
            ChatDownloadOverride.ALWAYS -> return true
            ChatDownloadOverride.NEVER -> return false
            ChatDownloadOverride.DEFAULT -> Unit
        }
        if (!isEnabled(network, kind)) return false
        if (network != NetworkKind.WIFI && sizeBytes > maxMobileSizeMb() * 1024L * 1024L) return false
        return true
    }
}
