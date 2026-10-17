package com.omidgame.mench.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Latest message of a conversation, for the chat-list preview. */
data class LastMessageRow(
    val conversationId: String,
    val body: String?,
    val kind: String,
    val senderId: String,
    val createdAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
)

/** A text message reduced to what the yearly statistics need. */
data class YearTextRow(val body: String?, val senderId: String, val createdAtEpochMillis: Long)

/** Number of received, not-yet-read messages in a conversation. */
data class UnreadRow(val conversationId: String, val unread: Int)

@Dao
interface MessageDao {
    /** Phase 3A: every non-deleted text message of a conversation since [sinceMillis], for the Year in Love statistics. */
    @Query(
        """
        SELECT body, senderId, createdAtEpochMillis FROM messages
        WHERE conversationId = :conversationId AND kind = 'text' AND body IS NOT NULL
          AND deletedAtEpochMillis IS NULL AND createdAtEpochMillis >= :sinceMillis
        """
    )
    suspend fun yearTextRows(conversationId: String, sinceMillis: Long): List<YearTextRow>

    @Query(
        """
        SELECT m.conversationId AS conversationId, m.body AS body, m.kind AS kind, m.senderId AS senderId,
               m.createdAtEpochMillis AS createdAtEpochMillis, m.deletedAtEpochMillis AS deletedAtEpochMillis
        FROM messages m
        WHERE m.createdAtEpochMillis = (
            SELECT MAX(m2.createdAtEpochMillis) FROM messages m2 WHERE m2.conversationId = m.conversationId
        )
        """,
    )
    fun observeLastMessages(): Flow<List<LastMessageRow>>

    @Query(
        """
        SELECT m.conversationId AS conversationId, COUNT(*) AS unread
        FROM messages m
        JOIN conversations c ON c.id = m.conversationId
        WHERE m.senderId != :selfId AND m.sequence IS NOT NULL AND m.sequence > c.lastReadSequence
          AND m.deletedAtEpochMillis IS NULL
        GROUP BY m.conversationId
        """,
    )
    fun observeUnreadCounts(selfId: String): Flow<List<UnreadRow>>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    /** Phase 4C backup: every stored message of a chat, oldest first (one-shot). */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC")
    suspend fun getAllForConversationOnce(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    /** Phase 1B: the newest [limit] messages, oldest first. Keeps a 10,000-message chat light; the window grows on scroll. */
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages WHERE conversationId = :conversationId
            ORDER BY createdAtEpochMillis DESC LIMIT :limit
        ) ORDER BY createdAtEpochMillis ASC
        """,
    )
    fun observeWindow(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    /** Removes a message that was never accepted by the server (failed-send "delete"). */
    @Query("DELETE FROM messages WHERE clientMsgId = :clientMsgId AND serverId IS NULL")
    suspend fun deleteUnsent(clientMsgId: String)

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

    /** Phase 11: SENT -> DELIVERED only; a message that is already READ is never downgraded. */
    @Query("""
        UPDATE messages SET state = :deliveredState
        WHERE conversationId = :conversationId AND senderId = :selfUserId
          AND sequence IS NOT NULL AND sequence <= :upToSequence AND state = :sentState
    """)
    suspend fun markDeliveredUpTo(conversationId: String, selfUserId: String, upToSequence: Long, sentState: String, deliveredState: String)

    // Phase 4 (edit/delete/reactions): all keyed by serverId, not
    // clientMsgId — a message must already have been successfully sent
    // (and therefore have a serverId) before it can be edited, deleted,
    // or reacted to; the composer/bubble UI disables those actions on a
    // still-PENDING message rather than these queries needing to handle
    // a null serverId.

    @Query("SELECT * FROM messages WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE clientMsgId = :clientMsgId LIMIT 1")
    suspend fun getByClientMsgId(clientMsgId: String): MessageEntity?

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
