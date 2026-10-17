package com.omidgame.mench.core.notifications

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Starts/stops the background connection service. Call start() only while the app is visible (Android 12+ forbids starting a foreground service from the background). */
@Singleton
class RealtimeServiceLauncher @Inject constructor(@ApplicationContext private val context: Context) {
    fun start() {
        runCatching { AnniversaryWorker.schedule(context) }
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, RealtimeForegroundService::class.java))
        }
    }

    fun stop() {
        context.stopService(Intent(context, RealtimeForegroundService::class.java))
    }
}
