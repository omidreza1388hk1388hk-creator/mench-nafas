package com.omidgame.mench

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.omidgame.mench.core.security.AppLockCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

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
class MenchApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Registered once, for the whole process lifetime — see
    // AppLockCoordinator's doc comment for why it observes
    // ProcessLifecycleOwner rather than any one Activity.
    @Inject lateinit var appLockCoordinator: AppLockCoordinator

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockCoordinator)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
