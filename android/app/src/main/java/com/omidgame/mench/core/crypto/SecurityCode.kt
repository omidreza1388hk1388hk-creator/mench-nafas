package com.omidgame.mench.core.crypto

import java.security.MessageDigest

/**
 * Human-comparable "security code" derived from PUBLIC keys only (no secrets involved), so two people can
 * read it aloud or scan a QR in person and confirm nobody swapped a key. Pure JVM (testable).
 *
 * - [forPair]: order-independent, so both phones show the SAME 60 digits (12 groups of 5).
 * - [forKey]: one key on its own (30 digits, 6 groups) for the "my key" / "their key" read-only view.
 */
object SecurityCode {
    private const val DOMAIN_PAIR = "mench-seccode:v1:pair:"
    private const val DOMAIN_KEY = "mench-seccode:v1:key:"
    private const val QR_PREFIX = "mench-sec:v1:"

    fun forPair(keyA: String, keyB: String): String {
        val (first, second) = if (keyA <= keyB) keyA to keyB else keyB to keyA
        return digits(sha256(DOMAIN_PAIR + first + "|" + second), groups = 12)
    }

    fun forKey(key: String): String = digits(sha256(DOMAIN_KEY + key), groups = 6)

    /** Payload for the comparison QR. Equal payloads on both phones == equal keys on both phones. */
    fun qrPayload(keyA: String, keyB: String): String {
        val (first, second) = if (keyA <= keyB) keyA to keyB else keyB to keyA
        val hash = sha256(DOMAIN_PAIR + first + "|" + second)
        return QR_PREFIX + hash.joinToString("") { "%02x".format(it) }
    }

    /** True when a scanned QR matches the code this phone computed for the same pair. */
    fun qrMatches(scanned: String, keyA: String, keyB: String): Boolean =
        scanned.trim() == qrPayload(keyA, keyB)

    /** "12345 67890 ..." -> as written; callers format with spaces already. */
    private fun digits(hash: ByteArray, groups: Int): String =
        (0 until groups).joinToString(" ") { g ->
            var v = 0L
            for (i in 0 until 5) v = (v shl 8) or (hash[(g * 5 + i) % hash.size].toLong() and 0xff)
            (v % 100_000L).toString().padStart(5, '0')
        }

    private fun sha256(s: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
}

/** Trust-on-first-use decision for a partner's public key. Pure. */
enum class KeyTrust { FIRST_USE, SAME, CHANGED }

object KeyTrustPolicy {
    fun evaluate(pinned: String?, current: String): KeyTrust = when {
        pinned == null -> KeyTrust.FIRST_USE
        pinned == current -> KeyTrust.SAME
        else -> KeyTrust.CHANGED
    }
}
