package com.omidgame.mench.core.security

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns an app-lock PIN into a salted PBKDF2 hash. The PIN itself is never
 * persisted anywhere, on disk or in memory beyond the call stack that
 * checks it — only [PinHash.saltHex]/[PinHash.hashHex] are ever stored,
 * by [AppLockStore] (spec sections 32/34: never store plaintext PIN).
 *
 * PBKDF2WithHmacSHA256 is a JDK-standard primitive available on every
 * Android API level this app targets (minSdk 26) — no third-party crypto
 * library needed, and nothing here is an invented algorithm (spec 35).
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    data class PinHash(val saltHex: String, val hashHex: String)

    fun hash(pin: String): PinHash {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = deriveHex(pin, salt)
        return PinHash(saltHex = salt.toHex(), hashHex = hash)
    }

    /** Constant-shape comparison: re-derives the hash from the candidate PIN and compares digests, never the PIN text. */
    fun matches(candidatePin: String, stored: PinHash): Boolean {
        val candidateHash = deriveHex(candidatePin, stored.saltHex.fromHex())
        return constantTimeEquals(candidateHash, stored.hashHex)
    }

    private fun deriveHex(pin: String, salt: ByteArray): String {
        val spec: KeySpec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.toHex()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
