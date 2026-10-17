package com.omidgame.mench.core.di

import com.omidgame.mench.feature.appearance.data.AppearanceRepositoryImpl
import com.omidgame.mench.feature.appearance.domain.AppearanceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppearanceBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAppearanceRepository(impl: AppearanceRepositoryImpl): AppearanceRepository
}
