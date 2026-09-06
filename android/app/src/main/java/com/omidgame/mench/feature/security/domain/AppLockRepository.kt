package com.omidgame.mench.feature.security.domain

/**
 * Domain-facing contract for the app-lock feature. App lock is entirely
 * local/device-side — spec section 32 — so unlike AuthRepository this has
 * no network-backed implementation; [feature.security.data.AppLockRepositoryImpl]
 * is the only implementation and talks straight to
 * [com.omidgame.mench.core.security.AppLockStore].
 */
interface AppLockRepository {
    /** App lock is "on" exactly when a PIN has been set — there is no separate enabled/disabled flag to fall out of sync with it. */
    suspend fun isAppLockEnabled(): Boolean

    suspend fun setPin(pin: String)

    /** Verifies against the stored hash. Returns false (not an exception) for "no PIN set" or "wrong PIN" alike — callers only need to know whether to proceed. */
    suspend fun verifyPin(pin: String): Boolean

    /** Removes the PIN and any biometric opt-in with it — disabling app lock always requires the current PIN, enforced by the caller via [verifyPin] first. */
    suspend fun disableAppLock()

    suspend fun isBiometricEnabled(): Boolean

    /** No-op (and returns false) if no PIN is set — biometric is a secondary unlock path layered on top of a PIN, never a replacement for one. */
    suspend fun setBiometricEnabled(enabled: Boolean): Boolean

    suspend fun autoLockTimeout(): AppLockTimeoutOption

    suspend fun setAutoLockTimeout(option: AppLockTimeoutOption)

    /** Called whenever the app is confirmed unlocked (successful PIN/biometric check, or lock just enabled) — resets the elapsed-time clock used by [shouldLock]. */
    suspend fun noteActive()

    /** True if the app was backgrounded (or never marked active) longer ago than the configured timeout. Always false if app lock is disabled. */
    suspend fun shouldLock(): Boolean
}
