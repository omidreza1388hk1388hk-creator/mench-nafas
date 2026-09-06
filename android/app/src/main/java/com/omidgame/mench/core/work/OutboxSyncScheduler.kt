package com.omidgame.mench.core.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val UNIQUE_WORK_NAME = "mench-outbox-sync"

/**
 * Thin wrapper around WorkManager so the rest of the app (ChatRepositoryImpl)
 * never touches WorkManager APIs directly — same "one seam, one place to
 * change" reasoning as SmsProvider/RealtimeBroadcaster on the backend.
 */
@Singleton
class OutboxSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    /**
     * Call after writing a new Outbox row, and once at app start to flush
     * anything left over from a previous session. APPEND_OR_REPLACE means
     * repeated calls while one run is already in flight chain onto it
     * rather than racing a second concurrent run against the same table.
     */
    fun scheduleNow() {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
