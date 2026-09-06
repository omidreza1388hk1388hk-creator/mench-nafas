package com.omidgame.mench.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface OutboxDao {
    @Upsert
    suspend fun upsert(entry: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE state != :failedState ORDER BY createdAtEpochMillis ASC")
    suspend fun listPending(failedState: String): List<OutboxEntity>

    @Query("UPDATE outbox SET state = :state, retryCount = retryCount + 1, lastAttemptAtEpochMillis = :attemptedAt WHERE operationId = :operationId")
    suspend fun markAttempt(operationId: String, state: String, attemptedAt: Long)

    @Query("DELETE FROM outbox WHERE operationId = :operationId")
    suspend fun delete(operationId: String)

    @Query("SELECT COUNT(*) FROM outbox WHERE state = :state")
    suspend fun countByState(state: String): Int
}
