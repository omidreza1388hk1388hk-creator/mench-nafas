package com.omidgame.mench.core.di

import android.content.Context
import androidx.room.Room
import com.omidgame.mench.core.database.AppDatabase
import com.omidgame.mench.core.database.ConversationDao
import com.omidgame.mench.core.database.MIGRATION_1_2
import com.omidgame.mench.core.database.MIGRATION_2_3
import com.omidgame.mench.core.database.MIGRATION_3_4
import com.omidgame.mench.core.database.MIGRATION_4_5
import com.omidgame.mench.core.database.MIGRATION_5_6
import com.omidgame.mench.core.database.MIGRATION_6_7
import com.omidgame.mench.core.database.MessageDao
import com.omidgame.mench.core.database.OutboxDao
import com.omidgame.mench.core.database.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "mench.db")
            // No fallbackToDestructiveMigration: a missing Migration object
            // must fail loudly during development rather than silently
            // wiping the user's offline data in production (spec 13/41/46).
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()
    }

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideOutboxDao(db: AppDatabase): OutboxDao = db.outboxDao()
}
