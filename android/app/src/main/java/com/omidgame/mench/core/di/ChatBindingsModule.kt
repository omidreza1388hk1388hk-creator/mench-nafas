package com.omidgame.mench.core.di

import com.omidgame.mench.feature.chat.data.ChatRepositoryImpl
import com.omidgame.mench.feature.chat.domain.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatBindingsModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
