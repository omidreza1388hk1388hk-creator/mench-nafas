package com.omidgame.mench.core.di

import com.omidgame.mench.feature.calls.data.CallRepositoryImpl
import com.omidgame.mench.feature.calls.domain.CallRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CallsBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCallRepository(impl: CallRepositoryImpl): CallRepository
}
