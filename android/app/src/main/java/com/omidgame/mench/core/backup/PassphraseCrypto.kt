package com.omidgame.mench.core.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase encryption for the data export. Same primitives and parameters as [BackupManager]
 * (PBKDF2-HMAC-SHA256, 210,000 iterations, AES-256-GCM) with its own file marker so an export can never be
 * mistaken for a backup. Layout: "MENCHEXP1" | salt(16) | iv(12) | ciphertext+tag. Pure JVM, unit-tested.
 */
object PassphraseCrypto {
    private val MAGIC = "MENCHEXP1".toByteArray()
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    fun encrypt(passphrase: String, plain: ByteArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(rnd::nextBytes)
        val iv = ByteArray(IV_BYTES).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        }
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    /** Returns null for a wrong passphrase, a damaged file, or a file that is not an export. */
    fun decrypt(passphrase: String, data: ByteArray): ByteArray? {
        val header = MAGIC.size + SALT_BYTES + IV_BYTES
        if (data.size <= header || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = data.copyOfRange(MAGIC.size + SALT_BYTES, header)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
            }.doFinal(data, header, data.size - header)
        } catch (_: AEADBadTagException) {
            null
        }
    }

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }
}

enum class PassphraseCheck { OK, TOO_SHORT, MISMATCH }

object PassphraseRules {
    const val MIN_LENGTH = 8

    fun check(passphrase: String, confirm: String): PassphraseCheck = when {
        passphrase.length < MIN_LENGTH -> PassphraseCheck.TOO_SHORT
        passphrase != confirm -> PassphraseCheck.MISMATCH
        else -> PassphraseCheck.OK
    }
}
