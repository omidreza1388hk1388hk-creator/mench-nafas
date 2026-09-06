package com.omidgame.mench.core.di

import android.content.Context
import coil.ImageLoader
import com.omidgame.mench.core.network.AuthenticatedClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Coil needs its own ImageLoader built on the @AuthenticatedClient
 * OkHttpClient — attachment content/thumbnail URLs (see AttachmentUrls)
 * require the same bearer token as every other API call. Without this,
 * Coil would use its own default client with no Authorization header and
 * every image load would 401.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @AuthenticatedClient okHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(okHttpClient)
        .build()
}
