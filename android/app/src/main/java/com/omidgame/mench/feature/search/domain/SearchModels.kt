package com.omidgame.mench.feature.search.domain

enum class SearchScope {
    ALL,
    MESSAGES,
    CONVERSATIONS,
    FILES,
}

data class MessageSearchResult(
    val id: String,
    val conversationId: String,
    val kind: String,
    val body: String?,
    val createdAtEpochMillis: Long,
    /** Group title, or the other member's display name for a direct conversation — see backend's toMessageSearchResultDto. Used so a result row can show "in <chat name>" without a second lookup. */
    val conversationDisplayName: String,
)

data class ConversationSearchResult(
    val id: String,
    val isGroup: Boolean,
    val displayName: String,
)

data class FileSearchResult(
    val id: String,
    val conversationId: String,
    val kind: String, // "image" | "file" | "audio" | "video"
    val originalFilename: String,
    val sizeBytes: Long,
    val createdAtEpochMillis: Long,
)

/**
 * isFromCache marks a result set produced by SearchRepositoryImpl's
 * offline fallback (local Room LIKE query) rather than the server's
 * pg_trgm-backed search — the UI surfaces this as a small "showing
 * offline results" note rather than silently passing off a narrower,
 * cached result set as equivalent to a live one (spec section 13: cached
 * search must never be indistinguishable from a real network failure).
 */
data class SearchResults(
    val messages: List<MessageSearchResult>,
    val conversations: List<ConversationSearchResult>,
    val files: List<FileSearchResult>,
    val isFromCache: Boolean,
) {
    val isEmpty: Boolean get() = messages.isEmpty() && conversations.isEmpty() && files.isEmpty()
}

enum class SearchFailureReason {
    NETWORK_UNAVAILABLE,
    UNKNOWN,
}

sealed interface SearchOutcome<out T> {
    data class Success<T>(val value: T) : SearchOutcome<T>
    data class Failure(val reason: SearchFailureReason) : SearchOutcome<Nothing>
}
