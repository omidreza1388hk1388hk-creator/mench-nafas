package com.omidgame.mench.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String?,
    val otherUserId: String?,
    val otherUserDisplayName: String?,
    val otherUserPhoneE164: String?,
    val lastMessageAtEpochMillis: Long?,
    val lastReadSequence: Long,
)
