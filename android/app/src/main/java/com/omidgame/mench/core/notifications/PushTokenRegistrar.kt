package com.omidgame.mench.core.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.RegisterPushTokenBody
import com.omidgame.mench.core.security.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers this device's current FCM token with the backend. Every
 * failure path here is deliberately silent-but-logged rather than
 * propagated: a device with no push token just means that device gets no
 * push notifications (falls back to the WebSocket path while foregrounded,
 * same as before Phase 6 existed) — it must never block sign-in, app
 * startup, or anything else.
 *
 * The most common "failure" isn't an error at all: a debug/CI build with
 * no google-services.json produces no default FirebaseApp, which is the
 * expected state for local development without real Firebase credentials
 * (mirrors the backend's NotificationsModule falling back to
 * DevNotificationProvider for the same reason — see that module's doc
 * comment). FirebaseApp.getApps(context).isEmpty() below detects exactly
 * that case up front, before ever touching FirebaseMessaging (which would
 * otherwise throw IllegalStateException: "Default FirebaseApp is not
 * initialized").
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatApi: ChatApi,
    private val tokenStore: TokenStore,
) {
    /**
     * Call after sign-in, and again on every cold start (see
     * MenchApplication.onCreate). Cheap and idempotent server-side (see
     * devices.repository.ts's updatePushToken doc comment) — no need to
     * locally track "did this already run" or diff against the
     * previously-sent token; just re-send it.
     */
    suspend fun registerCurrentToken() {
        val token = fetchTokenOrNull() ?: return
        registerToken(token)
    }

    /** Called directly from MenchFirebaseMessagingService.onNewToken — the token is already in hand there, no need to re-fetch it. */
    suspend fun registerToken(token: String) = withContext(Dispatchers.IO) {
        val tokens = tokenStore.read()
        if (tokens == null) {
            // Not signed in — nothing to register against yet.
            // registerCurrentToken() runs again right after sign-in
            // completes (see AuthRepositoryImpl.verifyOtp), which covers
            // this case once the user actually has a session.
            return@withContext
        }
        try {
            chatApi.registerPushToken(tokens.deviceId, RegisterPushTokenBody(token = token))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register push token with server (will retry on next app start)", e)
        }
    }

    private suspend fun fetchTokenOrNull(): String? = withContext(Dispatchers.IO) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.i(TAG, "No default FirebaseApp configured (no google-services.json) — skipping push token registration")
            return@withContext null
        }
        try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to obtain FCM token", e)
            null
        }
    }

    private companion object {
        const val TAG = "PushTokenRegistrar"
    }
}
