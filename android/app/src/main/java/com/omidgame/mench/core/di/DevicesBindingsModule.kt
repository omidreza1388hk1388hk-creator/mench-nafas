package com.omidgame.mench.core.di

import com.omidgame.mench.feature.devices.data.DevicesRepositoryImpl
import com.omidgame.mench.feature.devices.domain.DevicesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DevicesBindingsModule {

    @Binds
    @Singleton
    abstract fun bindDevicesRepository(impl: DevicesRepositoryImpl): DevicesRepository
}
