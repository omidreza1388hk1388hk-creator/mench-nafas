package com.omidgame.mench.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class StoredTokens(val accessToken: String, val refreshToken: String, val deviceId: String)

/**
 * Persists auth tokens using EncryptedSharedPreferences, which wraps its
 * AES-256-GCM data key in the Android Keystore (hardware-backed on
 * supported devices). Tokens never touch plain DataStore/SharedPreferences
 * and are never logged (spec sections 3/15/32).
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "mench_secure_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun save(tokens: StoredTokens) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putString(KEY_DEVICE_ID, tokens.deviceId)
            .apply()
    }

    suspend fun read(): StoredTokens? = withContext(Dispatchers.IO) {
        val access = prefs.getString(KEY_ACCESS, null)
        val refresh = prefs.getString(KEY_REFRESH, null)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (access != null && refresh != null && deviceId != null) StoredTokens(access, refresh, deviceId) else null
    }

    /**
     * Phase 6: refreshToken()/token-refresh responses don't carry
     * deviceId (see AuthApi.RefreshTokenResponse — it never changes
     * across a refresh, only the tokens do), so a refresh must preserve
     * whatever deviceId is already stored rather than overwriting
     * StoredTokens wholesale and losing it. Call this instead of save()
     * from the token-refresh path.
     */
    suspend fun updateAccessAndRefreshTokens(accessToken: String, refreshToken: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_DEVICE_ID = "device_id"
    }
}
