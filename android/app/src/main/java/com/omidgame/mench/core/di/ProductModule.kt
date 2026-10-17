package com.omidgame.mench.core.di

import com.omidgame.mench.BuildConfig
import com.omidgame.mench.core.network.AuthenticatedClient
import com.omidgame.mench.core.network.EcosystemApi
import com.omidgame.mench.core.network.SyncApi
import com.omidgame.mench.core.network.ProductApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProductModule {
    @Provides
    @Singleton
    fun provideProductApi(@AuthenticatedClient client: OkHttpClient, moshi: Moshi): ProductApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ProductApi::class.java)

    @Provides
    @Singleton
    fun provideEcosystemApi(@AuthenticatedClient client: OkHttpClient, moshi: Moshi): EcosystemApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(EcosystemApi::class.java)

    @Provides
    @Singleton
    fun provideSyncApi(@AuthenticatedClient client: OkHttpClient, moshi: Moshi): SyncApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SyncApi::class.java)
}
