package com.omidgame.mench.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists app-lock state the same way [TokenStore] persists auth tokens:
 * EncryptedSharedPreferences backed by an Android Keystore master key.
 * Only the PIN's salt+hash are stored, never the PIN itself (spec 32/34).
 * Kept as its own file (not reusing "mench_secure_tokens") so wiping auth
 * tokens on logout never accidentally wipes — or is blocked by — the
 * user's independent app-lock configuration.
 */
@Singleton
class AppLockStore @Inject constructor(
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
            "mench_secure_applock",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun savePin(hash: PinHasher.PinHash) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_SALT, hash.saltHex)
            .putString(KEY_HASH, hash.hashHex)
            .apply()
    }

    suspend fun readPin(): PinHasher.PinHash? = withContext(Dispatchers.IO) {
        val salt = prefs.getString(KEY_SALT, null)
        val hash = prefs.getString(KEY_HASH, null)
        if (salt != null && hash != null) PinHasher.PinHash(salt, hash) else null
    }

    /** Clears the PIN and biometric opt-in together — disabling app lock means there is no lock configuration left at all, not a half-configured one. */
    suspend fun clearPin() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .remove(KEY_BIOMETRIC_ENABLED)
            .apply()
    }

    suspend fun isBiometricEnabled(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    suspend fun autoLockTimeoutSeconds(): Int = withContext(Dispatchers.IO) {
        prefs.getInt(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
    }

    suspend fun setAutoLockTimeoutSeconds(seconds: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(KEY_TIMEOUT_SECONDS, seconds).apply()
    }

    /** Null means "never recorded" — either the app has never been backgrounded since lock was enabled, or a fresh install. Callers treat that as "must unlock". */
    suspend fun lastActiveAtEpochMillis(): Long? = withContext(Dispatchers.IO) {
        val value = prefs.getLong(KEY_LAST_ACTIVE_AT, -1L)
        if (value == -1L) null else value
    }

    suspend fun recordActiveNow() = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_LAST_ACTIVE_AT, System.currentTimeMillis()).apply()
    }

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_TIMEOUT_SECONDS = "auto_lock_timeout_seconds"
        const val KEY_LAST_ACTIVE_AT = "last_active_at_epoch_millis"
        const val DEFAULT_TIMEOUT_SECONDS = 30
    }
}
