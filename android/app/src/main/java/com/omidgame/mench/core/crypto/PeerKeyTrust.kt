package com.omidgame.mench.core.crypto

import android.content.Context

/**
 * Remembers which public key we trusted for each partner (trust on first use) and whether a different key has
 * appeared since. Public keys are not secrets, so plain SharedPreferences is fine. The decision logic itself is
 * [KeyTrustPolicy] (pure, unit-tested); this class only stores the result.
 */
object PeerKeyTrust {
    private const val PREFS = "mench_e2ee_pins"
    private const val PIN = "pin_"
    private const val PENDING = "pending_"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when it is OK to encrypt to [currentKey]. A changed key returns false and is remembered as pending. */
    fun check(context: Context, userId: String, currentKey: String): Boolean {
        val p = prefs(context)
        return when (KeyTrustPolicy.evaluate(p.getString(PIN + userId, null), currentKey)) {
            KeyTrust.FIRST_USE -> { p.edit().putString(PIN + userId, currentKey).apply(); true }
            KeyTrust.SAME -> { p.edit().remove(PENDING + userId).apply(); true }
            KeyTrust.CHANGED -> { p.edit().putString(PENDING + userId, currentKey).apply(); false }
        }
    }

    fun hasPendingChange(context: Context, userId: String): Boolean = prefs(context).contains(PENDING + userId)

    /** The user confirmed the new key (ideally after comparing the security code): trust it from now on. */
    fun acknowledge(context: Context, userId: String) {
        val p = prefs(context)
        val pending = p.getString(PENDING + userId, null) ?: return
        p.edit().putString(PIN + userId, pending).remove(PENDING + userId).apply()
    }

    fun pendingUserIds(context: Context): Set<String> =
        prefs(context).all.keys.filter { it.startsWith(PENDING) }.map { it.removePrefix(PENDING) }.toSet()
}
