package com.omidgame.mench.core.di

import android.content.Context
import android.os.Build
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import android.app.ActivityManager
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
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
    ): ImageLoader {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = activityManager?.isLowRamDevice == true
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            // Phase 2A: animated GIFs (ImageDecoder on API 28+, the bundled decoder below that).
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            // Phase 1B tuning: smaller memory share on low-RAM phones, a bounded disk cache,
            // RGB_565 on low-RAM devices (half the memory per bitmap) and a short crossfade.
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(if (lowRam) 0.10 else 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (lowRam) 100L * 1024 * 1024 else 250L * 1024 * 1024)
                    .build()
            }
            // Attachment URLs are authenticated and may carry no-cache headers; the same
            // attachment id always maps to the same bytes, so cache regardless of headers.
            .respectCacheHeaders(false)
            .allowRgb565(lowRam)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(180)
            .build()
    }
}
