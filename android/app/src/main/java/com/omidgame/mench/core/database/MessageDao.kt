package com.omidgame.mench.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT MAX(sequence) FROM messages WHERE conversationId = :conversationId")
    suspend fun highestSequence(conversationId: String): Long?

    @Query("UPDATE messages SET state = :state WHERE clientMsgId = :clientMsgId")
    suspend fun updateState(clientMsgId: String, state: String)

    @Query("""
        UPDATE messages
        SET state = :state, serverId = :serverId, sequence = :sequence,
            createdAtEpochMillis = :createdAtEpochMillis, attachmentId = :attachmentId,
            attachmentWidthPx = COALESCE(:widthPx, attachmentWidthPx),
            attachmentHeightPx = COALESCE(:heightPx, attachmentHeightPx)
        WHERE clientMsgId = :clientMsgId
    """)
    suspend fun markSent(
        clientMsgId: String,
        serverId: String,
        sequence: Long,
        createdAtEpochMillis: Long,
        state: String,
        attachmentId: String? = null,
        widthPx: Int? = null,
        heightPx: Int? = null,
    )

    @Query("""
        UPDATE messages SET state = :readState
        WHERE conversationId = :conversationId AND senderId = :selfUserId
          AND sequence IS NOT NULL AND sequence <= :upToSequence AND state != :readState
    """)
    suspend fun markReadUpTo(conversationId: String, selfUserId: String, upToSequence: Long, readState: String)

    // Phase 4 (edit/delete/reactions): all keyed by serverId, not
    // clientMsgId — a message must already have been successfully sent
    // (and therefore have a serverId) before it can be edited, deleted,
    // or reacted to; the composer/bubble UI disables those actions on a
    // still-PENDING message rather than these queries needing to handle
    // a null serverId.

    @Query("SELECT * FROM messages WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): MessageEntity?

    @Query("UPDATE messages SET body = :body, editedAtEpochMillis = :editedAtEpochMillis WHERE serverId = :serverId")
    suspend fun markEdited(serverId: String, body: String, editedAtEpochMillis: Long)

    @Query("UPDATE messages SET deletedAtEpochMillis = :deletedAtEpochMillis WHERE serverId = :serverId")
    suspend fun markDeleted(serverId: String, deletedAtEpochMillis: Long)

    @Query("UPDATE messages SET reactionsJson = :reactionsJson WHERE serverId = :serverId")
    suspend fun updateReactions(serverId: String, reactionsJson: String?)

    /** Phase 6 offline search fallback — matches by message body only (attachment filenames aren't indexed here); excludes deleted messages, matching what the server-side search endpoint would return. */
    @Query("""
        SELECT * FROM messages
        WHERE body LIKE '%' || :query || '%' AND deletedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis DESC LIMIT :limit
    """)
    suspend fun searchLocal(query: String, limit: Int): List<MessageEntity>
}
