package com.omidgame.mench.feature.settings.domain

enum class SwipeReplyDirection(val storageValue: String) {
    ANY("any"), // both directions (the behaviour before this setting existed)
    START("start"), // towards the reading start side (right in LTR, left in RTL)
    END("end"),
    ;

    companion object { fun fromStorage(v: String?) = entries.firstOrNull { it.storageValue == v } ?: ANY }
}

enum class PhotoQuality(val storageValue: String) {
    COMPRESSED("compressed"), ORIGINAL("original");

    companion object { fun fromStorage(v: String?) = entries.firstOrNull { it.storageValue == v } ?: COMPRESSED }
}

/** Everything the "Chats" settings control. Defaults reproduce today's behaviour exactly. Pure Kotlin. */
data class ChatSettingsSnapshot(
    val messageTextScale: Float = 1f,
    val lineSpacing: Float = 1f,
    val enterSends: Boolean = false,
    val autoCapitalize: Boolean = true,
    val spellCheck: Boolean = true,
    val linkPreviews: Boolean = true,
    val quickReactions: List<String> = ChatSettingsRules.DEFAULT_QUICK_REACTIONS,
    val swipeReply: SwipeReplyDirection = SwipeReplyDirection.ANY,
    /** false = an archived chat returns to the main list on a new message (today's behaviour). */
    val keepArchived: Boolean = false,
    val photoQuality: PhotoQuality = PhotoQuality.COMPRESSED,
    val stripLocation: Boolean = true,
    val viewOnceDefault: Boolean = false,
)

object ChatSettingsRules {
    const val MIN_TEXT_SCALE = 0.85f
    const val MAX_TEXT_SCALE = 1.5f
    const val MIN_LINE_SPACING = 1f
    const val MAX_LINE_SPACING = 1.6f
    const val MAX_QUICK_REACTIONS = 6

    /**
     * The eight emoji ChatScreen had hard-coded before this setting existed (so nothing changes until the user picks
     * their own): thumbs up, heart, heart-eyes, smiling-with-hearts, laugh, wow, sad, pray. A CUSTOM choice is capped at six.
     */
    val DEFAULT_QUICK_REACTIONS: List<String> = listOf(
        "\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE0D", "\uD83E\uDD70", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F",
    )

    fun clampTextScale(v: Float): Float = v.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
    fun clampLineSpacing(v: Float): Float = v.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING)

    /** Max 6, no blanks, no duplicates, order kept; an empty result falls back to the defaults so the bar is never empty. */
    fun sanitizeQuickReactions(input: List<String>): List<String> =
        input.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_QUICK_REACTIONS)
            .ifEmpty { DEFAULT_QUICK_REACTIONS }

    fun encodeReactions(list: List<String>): String = sanitizeQuickReactions(list).joinToString("|")
    fun decodeReactions(raw: String?): List<String> =
        if (raw.isNullOrBlank()) DEFAULT_QUICK_REACTIONS else sanitizeQuickReactions(raw.split('|'))

    /** Toggle [emoji] in the picker: remove if present, add if there is room, otherwise ignore. */
    fun toggleReaction(current: List<String>, emoji: String): List<String> = when {
        emoji in current -> if (current.size > 1) current - emoji else current // keep at least one
        current.size < MAX_QUICK_REACTIONS -> current + emoji
        else -> current
    }

    /**
     * Should a drag of [dx] pixels count as swipe-to-reply? [isRtl] flips which physical side is "start".
     * Positive dx = finger moves right.
     */
    fun swipeAccepted(direction: SwipeReplyDirection, dx: Float, isRtl: Boolean): Boolean = when (direction) {
        SwipeReplyDirection.ANY -> true
        SwipeReplyDirection.START -> if (isRtl) dx < 0 else dx > 0
        SwipeReplyDirection.END -> if (isRtl) dx > 0 else dx < 0
    }
}
