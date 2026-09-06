package com.omidgame.mench.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of the signed-in user's own profile, so the app has
 * something to render offline immediately on launch (spec section 13:
 * offline-first — cached profile/settings remain available without
 * network). This is not a general user directory; other users' profiles
 * are cached per-conversation starting in Phase 2/3.
 */
@Entity(tableName = "cached_self_user")
data class UserEntity(
    @PrimaryKey val id: String,
    val phoneE164: String,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String?,
    val bio: String? = null,
    val updatedAtEpochMillis: Long,
)
