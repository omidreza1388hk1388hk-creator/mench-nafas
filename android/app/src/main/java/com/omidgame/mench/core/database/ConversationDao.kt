package com.omidgame.mench.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Upsert
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageAtEpochMillis DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET togetherSince = :togetherSince, nextMeetDate = :nextMeetDate WHERE id = :id")
    suspend fun updateUsDates(id: String, togetherSince: String?, nextMeetDate: String?)

    @Query("UPDATE conversations SET isMuted = :isMuted WHERE id = :id")
    suspend fun updateMuted(id: String, isMuted: Boolean)

    @Query("UPDATE conversations SET pinnedMessageId = :messageId WHERE id = :id")
    suspend fun updatePinnedMessage(id: String, messageId: String?)

    @Query("UPDATE conversations SET isArchived = :isArchived WHERE id = :id")
    suspend fun updateArchived(id: String, isArchived: Boolean)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinned(id: String, isPinned: Boolean)

    /** One-shot read for places without a Flow collector (the home-screen widget). */
    @Query("SELECT * FROM conversations")
    suspend fun getAllOnce(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("UPDATE conversations SET lastMessageAtEpochMillis = :epochMillis WHERE id = :id")
    suspend fun updateLastMessageAt(id: String, epochMillis: Long)

    @Query("UPDATE conversations SET lastReadSequence = :sequence WHERE id = :id AND lastReadSequence < :sequence")
    suspend fun updateLastReadSequence(id: String, sequence: Long)

    /** Phase 6 — applied when a group.renamed realtime event arrives for a conversation already cached locally. observeAll()'s Flow re-emits automatically, so any open conversation list updates without a manual refresh. */
    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    /** Phase 6 — applied when the signed-in user is removed from (or leaves) a group, either via their own action or a group.member_removed/group.member_left realtime event for their own userId. A conversation the user is no longer part of has no reason to remain in the local cache. */
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Phase 6 offline search fallback — same reasoning as MessageDao.searchLocal. */
    @Query("""
        SELECT * FROM conversations
        WHERE title LIKE '%' || :query || '%'
           OR otherUserDisplayName LIKE '%' || :query || '%'
           OR otherUserPhoneE164 LIKE '%' || :query || '%'
        ORDER BY lastMessageAtEpochMillis DESC LIMIT :limit
    """)
    suspend fun searchLocal(query: String, limit: Int): List<ConversationEntity>
}
