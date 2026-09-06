package com.omidgame.mench.feature.search.domain

interface SearchRepository {
    /**
     * conversationId narrows to "search within this chat" (e.g. a chat
     * screen's own search-in-conversation action) — null searches
     * everywhere the user is a member, matching the backend's
     * SearchRepository.searchMessages/searchFiles semantics.
     */
    suspend fun search(
        query: String,
        scope: SearchScope,
        conversationId: String? = null,
    ): SearchOutcome<SearchResults>
}
