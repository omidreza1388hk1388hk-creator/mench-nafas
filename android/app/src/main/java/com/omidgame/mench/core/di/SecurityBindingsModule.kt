package com.omidgame.mench.core.di

import com.omidgame.mench.feature.security.data.AppLockRepositoryImpl
import com.omidgame.mench.feature.security.domain.AppLockRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAppLockRepository(impl: AppLockRepositoryImpl): AppLockRepository
}
