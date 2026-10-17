package com.omidgame.mench

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.omidgame.mench.core.security.AppLockCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Implements Configuration.Provider so WorkManager uses HiltWorkerFactory
 * — required for OutboxSyncWorker's @AssistedInject constructor to be
 * satisfied at all; without this, WorkManager's default factory can't
 * construct a worker that takes Hilt-injected dependencies, and every
 * enqueued OutboxSyncWorker run would crash instantly. The corresponding
 * manifest change (removing WorkManager's default auto-initializer) is in
 * AndroidManifest.xml — both halves of this wiring are required together.
 */
@HiltAndroidApp
class MenchApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    // Coil's default (singleton) loader. Without this, AsyncImage builds its own loader that has no
    // bearer token, so every authenticated attachment / avatar / GIF URL would answer 401. Lazy
    // because Coil may ask for the loader very early; it is only resolved on the first image request.
    @Inject lateinit var imageLoader: dagger.Lazy<ImageLoader>

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var productApi: dagger.Lazy<com.omidgame.mench.core.network.ProductApi>

    @Inject lateinit var chatApiLazy: dagger.Lazy<com.omidgame.mench.core.network.ChatApi>

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    // Registered once, for the whole process lifetime — see
    // AppLockCoordinator's doc comment for why it observes
    // ProcessLifecycleOwner rather than any one Activity.
    @Inject lateinit var appLockCoordinator: AppLockCoordinator

    /**
     * Phase 1D.1: the active account slot decides which preferences/database/files this process sees.
     * Must happen before anything touches storage, i.e. here.
     */
    override fun attachBaseContext(base: android.content.Context) {
        val slot = com.omidgame.mench.core.accounts.AccountRegistry.activeSlot(base)
        super.attachBaseContext(com.omidgame.mench.core.accounts.AccountContext(base, slot))
    }

    override fun onCreate() {
        super.onCreate()
        com.omidgame.mench.core.diagnostics.CrashLogger.install(this)
        com.omidgame.mench.core.crypto.E2eeManager.init(this)
        // Settings Phase 2: load the chat settings so Context-free code (the repository) sees the user's choices.
        com.omidgame.mench.feature.chat.presentation.ChatSettingsStore.load(this)
        com.omidgame.mench.core.webrtc.CallSettingsStore.load(this)
        // Settings Phase 2: once a day, remove old edited-photo scratch files.
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "cache_hygiene",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<com.omidgame.mench.core.media.CacheHygieneWorker>(1, java.util.concurrent.TimeUnit.DAYS).build(),
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockCoordinator)
        // Phase 4C: make sure this account has an E2EE identity and the server knows the public half.
        appScope.launch { com.omidgame.mench.core.crypto.E2eeManager.publish(chatApiLazy.get()) }
        // Phase 4B: send crashes from earlier runs (no-op when there are none / offline / signed out).
        appScope.launch {
            com.omidgame.mench.core.diagnostics.CrashLogger.uploadPending(this@MenchApplication, productApi.get(), BuildConfig.VERSION_NAME)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
