package com.omidgame.mench.feature.chat.presentation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultCryptoTest {
    private val key = VaultCrypto.deriveKey("our secret", "conv-1")

    @Test
    fun roundTripsPersianAndEmojiText() {
        val text = "یادداشت محرمانه 💗\nخط دوم"
        assertEquals(text, VaultCrypto.decrypt(key, VaultCrypto.encrypt(key, text)))
    }

    @Test
    fun wrongPassphraseOrOtherConversationCannotDecrypt() {
        val token = VaultCrypto.encrypt(key, "hello")
        assertNull(VaultCrypto.decrypt(VaultCrypto.deriveKey("wrong", "conv-1"), token))
        assertNull(VaultCrypto.decrypt(VaultCrypto.deriveKey("our secret", "conv-2"), token))
    }

    @Test
    fun tamperedOrMalformedTokensDecryptToNull() {
        val token = VaultCrypto.encrypt(key, "hello")
        val flipped = token.dropLast(3) + (if (token.takeLast(3) == "AAA") "BBB" else "AAA")
        assertNull(VaultCrypto.decrypt(key, flipped))
        assertNull(VaultCrypto.decrypt(key, "v2:" + token.removePrefix("v1:")))
        assertNull(VaultCrypto.decrypt(key, "v1:"))
        assertNull(VaultCrypto.decrypt(key, "garbage"))
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        assertNotEquals(VaultCrypto.encrypt(key, "same"), VaultCrypto.encrypt(key, "same"))
    }

    @Test
    fun keyDerivationIsDeterministic() {
        assertArrayEquals(key, VaultCrypto.deriveKey("our secret", "conv-1"))
    }

    @Test
    fun notePackingKeepsTitleAndBody() {
        assertEquals("T" to "line1\nline2", VaultCrypto.unpackNote(VaultCrypto.packNote("T", "line1\nline2")))
        assertEquals("a b" to "x", VaultCrypto.unpackNote(VaultCrypto.packNote("a\u0000b", "x")))
        assertEquals("" to "no separator", VaultCrypto.unpackNote("no separator"))
    }
}

class VaultPassphraseTest {
    private val items = listOf(com.omidgame.mench.feature.chat.domain.VaultItem("1", "blob-1", true), com.omidgame.mench.feature.chat.domain.VaultItem("2", "blob-2", false))

    @Test
    fun anyPassphraseIsAcceptedForAnEmptyVault() {
        org.junit.Assert.assertTrue(passphraseAccepted(emptyList()) { null })
    }

    @Test
    fun acceptedWhenAtLeastOneNoteDecrypts() {
        org.junit.Assert.assertTrue(passphraseAccepted(items) { if (it == "blob-2") "ok" else null })
        org.junit.Assert.assertFalse(passphraseAccepted(items) { null })
    }
}
