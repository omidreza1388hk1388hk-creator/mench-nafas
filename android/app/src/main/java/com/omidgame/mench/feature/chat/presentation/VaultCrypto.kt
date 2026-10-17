package com.omidgame.mench.feature.chat.presentation

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The couple's private vault: notes are encrypted on the phone with a passphrase the server never sees.
 *
 * - Key: PBKDF2-HMAC-SHA256 (120k iterations) from the passphrase, salted per conversation. Derived once per unlock.
 * - Each note: AES-256-GCM with a fresh random 12-byte IV; token is "v1:" + Base64(iv ‖ ciphertext+tag).
 * - A wrong passphrase or a tampered token fails the GCM tag check and decrypts to null.
 * There is no recovery: if both of you forget the passphrase the notes are gone.
 */
object VaultCrypto {
    private const val PREFIX = "v1:"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val NOTE_SEPARATOR = '\u0000'
    private val random = SecureRandom()

    fun deriveKey(passphrase: String, conversationId: String): ByteArray {
        val salt = MessageDigest.getInstance("SHA-256").digest(("mench-vault:" + conversationId).toByteArray(Charsets.UTF_8))
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(key: ByteArray, plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    /** The plaintext, or null if the token is malformed, tampered with, or [key] is wrong. */
    fun decrypt(key: ByteArray, token: String): String? = runCatching {
        if (!token.startsWith(PREFIX)) return null
        val raw = Base64.getDecoder().decode(token.removePrefix(PREFIX))
        if (raw.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, IV_BYTES)))
        String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    /** Title and body as one plaintext, so a single token holds a whole note. Titles cannot contain the separator. */
    fun packNote(title: String, body: String): String = title.replace(NOTE_SEPARATOR, ' ') + NOTE_SEPARATOR + body

    fun unpackNote(plaintext: String): Pair<String, String> {
        val i = plaintext.indexOf(NOTE_SEPARATOR)
        return if (i < 0) "" to plaintext else plaintext.substring(0, i) to plaintext.substring(i + 1)
    }
}
