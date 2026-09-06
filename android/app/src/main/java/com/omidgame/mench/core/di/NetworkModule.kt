package com.omidgame.mench.core.di

import com.omidgame.mench.BuildConfig
import com.omidgame.mench.core.network.AuthApi
import com.omidgame.mench.core.network.AuthenticatedAuthApi
import com.omidgame.mench.core.network.CallsApi
import com.omidgame.mench.core.network.AuthHeaderInterceptor
import com.omidgame.mench.core.network.TokenAuthenticator
import com.omidgame.mench.core.network.UnauthenticatedClient
import com.omidgame.mench.core.network.AuthenticatedClient
import com.omidgame.mench.core.network.RealtimeSocketClient
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.UsersApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private fun baseClientBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            // Logs headers/bodies only in debug builds, and even then the
            // access/refresh token values themselves are never included in
            // any log line anywhere in this app (spec section 15).
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }
        return builder
    }

    @UnauthenticatedClient
    @Provides
    @Singleton
    fun provideUnauthenticatedClient(): OkHttpClient = baseClientBuilder().build()

    @AuthenticatedClient
    @Provides
    @Singleton
    fun provideAuthenticatedClient(
        authHeaderInterceptor: AuthHeaderInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = baseClientBuilder()
        .addInterceptor(authHeaderInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(
        @UnauthenticatedClient client: OkHttpClient,
        moshi: Moshi,
    ): AuthApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideChatApi(
        @AuthenticatedClient client: OkHttpClient,
        moshi: Moshi,
    ): ChatApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ChatApi::class.java)
    }

    @RealtimeSocketClient
    @Provides
    @Singleton
    fun provideRealtimeSocketClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // no read timeout for a long-lived socket
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideUsersApi(
        @AuthenticatedClient client: OkHttpClient,
        moshi: Moshi,
    ): UsersApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(UsersApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthenticatedAuthApi(
        @AuthenticatedClient client: OkHttpClient,
        moshi: Moshi,
    ): AuthenticatedAuthApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AuthenticatedAuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCallsApi(
        @AuthenticatedClient client: OkHttpClient,
        moshi: Moshi,
    ): CallsApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CallsApi::class.java)
    }
}
