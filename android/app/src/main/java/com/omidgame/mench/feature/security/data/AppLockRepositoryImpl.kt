package com.omidgame.mench.feature.security.data

import com.omidgame.mench.core.security.AppLockStore
import com.omidgame.mench.core.security.PinHasher
import com.omidgame.mench.feature.security.domain.AppLockRepository
import com.omidgame.mench.feature.security.domain.AppLockTimeoutOption
import javax.inject.Inject

class AppLockRepositoryImpl @Inject constructor(
    private val store: AppLockStore,
) : AppLockRepository {

    override suspend fun isAppLockEnabled(): Boolean = store.readPin() != null

    override suspend fun setPin(pin: String) {
        store.savePin(PinHasher.hash(pin))
        // Setting a PIN just now must not immediately re-lock the user out
        // of the screen they set it from.
        noteActive()
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val stored = store.readPin() ?: return false
        return PinHasher.matches(pin, stored)
    }

    override suspend fun disableAppLock() {
        store.clearPin()
    }

    override suspend fun isBiometricEnabled(): Boolean = store.isBiometricEnabled()

    override suspend fun setBiometricEnabled(enabled: Boolean): Boolean {
        if (enabled && !isAppLockEnabled()) return false
        store.setBiometricEnabled(enabled)
        return true
    }

    override suspend fun autoLockTimeout(): AppLockTimeoutOption =
        AppLockTimeoutOption.fromSeconds(store.autoLockTimeoutSeconds())

    override suspend fun setAutoLockTimeout(option: AppLockTimeoutOption) {
        store.setAutoLockTimeoutSeconds(option.seconds)
    }

    override suspend fun noteActive() = store.recordActiveNow()

    override suspend fun shouldLock(): Boolean {
        if (!isAppLockEnabled()) return false
        val lastActive = store.lastActiveAtEpochMillis() ?: return true
        val timeoutMillis = store.autoLockTimeoutSeconds() * 1000L
        return System.currentTimeMillis() - lastActive >= timeoutMillis
    }
}
