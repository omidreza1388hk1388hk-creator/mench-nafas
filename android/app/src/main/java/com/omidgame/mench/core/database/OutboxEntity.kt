package com.omidgame.mench.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable record of a not-yet-confirmed operation. Rows here survive app
 * restart, process death, and device restart (they're just Room/SQLite
 * rows on disk) — OutboxSyncWorker (a WorkManager CoroutineWorker, so it
 * can run even if the app process isn't) is what actually retries them
 * (spec section 14). operationType is a string, not sealed to
 * SEND_MESSAGE, so later phases (e.g. a "mark read" or "delete message"
 * operation) can reuse this same table rather than inventing a parallel
 * mechanism.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val operationId: String,
    val operationType: String,
    val conversationId: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
    val retryCount: Int,
    val lastAttemptAtEpochMillis: Long?,
    val state: String, // OutboxState enum name
)

enum class OutboxState {
    PENDING,
    IN_FLIGHT,
    FAILED,
}

object OutboxOperationType {
    const val SEND_MESSAGE = "SEND_MESSAGE"
    const val SEND_ATTACHMENT_MESSAGE = "SEND_ATTACHMENT_MESSAGE"
    const val EDIT_MESSAGE = "EDIT_MESSAGE"
    const val DELETE_MESSAGE = "DELETE_MESSAGE"
    const val SET_REACTION = "SET_REACTION"
    const val CLEAR_REACTION = "CLEAR_REACTION"
    const val FORWARD_MESSAGE = "FORWARD_MESSAGE"
}
