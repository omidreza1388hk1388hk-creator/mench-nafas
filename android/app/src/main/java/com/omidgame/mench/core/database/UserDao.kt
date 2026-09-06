package com.omidgame.mench.core.database

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM cached_self_user LIMIT 1")
    fun observeSelf(): Flow<UserEntity?>

    @Query("SELECT * FROM cached_self_user LIMIT 1")
    suspend fun getSelfOnce(): UserEntity?

    @Query("DELETE FROM cached_self_user")
    suspend fun clear()
}
