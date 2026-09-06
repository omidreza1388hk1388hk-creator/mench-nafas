package com.omidgame.mench.feature.search.data

import com.omidgame.mench.core.database.ConversationDao
import com.omidgame.mench.core.database.MessageDao
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.feature.search.domain.ConversationSearchResult
import com.omidgame.mench.feature.search.domain.FileSearchResult
import com.omidgame.mench.feature.search.domain.MessageSearchResult
import com.omidgame.mench.feature.search.domain.SearchFailureReason
import com.omidgame.mench.feature.search.domain.SearchOutcome
import com.omidgame.mench.feature.search.domain.SearchRepository
import com.omidgame.mench.feature.search.domain.SearchResults
import com.omidgame.mench.feature.search.domain.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCAL_SEARCH_LIMIT = 30

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
) : SearchRepository {

    override suspend fun search(
        query: String,
        scope: SearchScope,
        conversationId: String?,
    ): SearchOutcome<SearchResults> = withContext(Dispatchers.IO) {
        try {
            val response = chatApi.search(
                query = query.ifBlank { null },
                scope = scope.toWireValue(),
                conversationId = conversationId,
            )
            SearchOutcome.Success(
                SearchResults(
                    messages = response.messages.map {
                        MessageSearchResult(
                            id = it.id,
                            conversationId = it.conversationId,
                            kind = it.kind,
                            body = it.body,
                            createdAtEpochMillis = Instant.parse(it.createdAt).toEpochMilli(),
                            conversationDisplayName = it.conversationTitle
                                ?: it.conversationOtherUserDisplayName
                                ?: "—",
                        )
                    },
                    conversations = response.conversations.map {
                        ConversationSearchResult(
                            id = it.id,
                            isGroup = it.kind == "group",
                            displayName = it.title ?: it.otherUserDisplayName ?: it.otherUserPhoneE164 ?: "—",
                        )
                    },
                    files = response.files.map {
                        FileSearchResult(
                            id = it.id,
                            conversationId = it.conversationId,
                            kind = it.kind,
                            originalFilename = it.originalFilename,
                            sizeBytes = it.sizeBytes,
                            createdAtEpochMillis = Instant.parse(it.createdAt).toEpochMilli(),
                        )
                    },
                    isFromCache = false,
                ),
            )
        } catch (e: IOException) {
            // Offline — fall back to whatever is already cached in Room
            // rather than surfacing a hard failure (spec section 13/43:
            // search must still work on cached data without a network).
            searchLocalOnly(query, scope)
        } catch (e: Exception) {
            SearchOutcome.Failure(SearchFailureReason.UNKNOWN)
        }
    }

    /**
     * Files aren't included in the offline fallback today — the local
     * Room schema flattens at most one attachment onto its owning
     * message row (MessageEntity), so a "files" search over cached data
     * would just be a filtered messages search anyway, and is left as a
     * documented gap (see docs/ARCHITECTURE.md's Phase 6 section) rather
     * than quietly duplicating messages search under a different label.
     */
    private suspend fun searchLocalOnly(query: String, scope: SearchScope): SearchOutcome<SearchResults> {
        val wantsMessages = scope == SearchScope.ALL || scope == SearchScope.MESSAGES
        val wantsConversations = scope == SearchScope.ALL || scope == SearchScope.CONVERSATIONS

        val messages = if (wantsMessages && query.isNotBlank()) {
            messageDao.searchLocal(query, LOCAL_SEARCH_LIMIT).mapNotNull { entity ->
                val conversation = conversationDao.getById(entity.conversationId)
                val displayName = conversation?.title
                    ?: conversation?.otherUserDisplayName
                    ?: conversation?.otherUserPhoneE164
                    ?: return@mapNotNull null
                MessageSearchResult(
                    id = entity.serverId ?: entity.clientMsgId,
                    conversationId = entity.conversationId,
                    kind = entity.kind,
                    body = entity.body,
                    createdAtEpochMillis = entity.createdAtEpochMillis,
                    conversationDisplayName = displayName,
                )
            }
        } else {
            emptyList()
        }

        val conversations = if (wantsConversations && query.isNotBlank()) {
            conversationDao.searchLocal(query, LOCAL_SEARCH_LIMIT).map { entity ->
                ConversationSearchResult(
                    id = entity.id,
                    isGroup = entity.kind == "group",
                    displayName = entity.title ?: entity.otherUserDisplayName ?: entity.otherUserPhoneE164 ?: "—",
                )
            }
        } else {
            emptyList()
        }

        return SearchOutcome.Success(
            SearchResults(messages = messages, conversations = conversations, files = emptyList(), isFromCache = true),
        )
    }

    private fun SearchScope.toWireValue(): String = when (this) {
        SearchScope.ALL -> "all"
        SearchScope.MESSAGES -> "messages"
        SearchScope.CONVERSATIONS -> "conversations"
        SearchScope.FILES -> "files"
    }
}
