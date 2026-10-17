package com.omidgame.mench.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PassphraseCryptoTest {
    private val plain = """{"user":"x","messages":["سلام"]}""".toByteArray(Charsets.UTF_8)

    @Test fun `round trip returns the original bytes`() {
        val enc = PassphraseCrypto.encrypt("correct horse", plain)
        assertThat(PassphraseCrypto.decrypt("correct horse", enc)).isEqualTo(plain)
    }

    @Test fun `ciphertext does not contain the plaintext and starts with the export marker`() {
        val enc = PassphraseCrypto.encrypt("correct horse", plain)
        assertThat(String(enc, Charsets.ISO_8859_1)).startsWith("MENCHEXP1")
        assertThat(String(enc, Charsets.ISO_8859_1)).doesNotContain("user")
    }

    @Test fun `wrong passphrase returns null`() {
        val enc = PassphraseCrypto.encrypt("correct horse", plain)
        assertThat(PassphraseCrypto.decrypt("wrong horse", enc)).isNull()
    }

    @Test fun `tampering is detected`() {
        val enc = PassphraseCrypto.encrypt("correct horse", plain)
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 1).toByte()
        assertThat(PassphraseCrypto.decrypt("correct horse", enc)).isNull()
    }

    @Test fun `a backup file or random bytes are not accepted as an export`() {
        assertThat(PassphraseCrypto.decrypt("x", "MENCHBAK1".toByteArray() + ByteArray(64))).isNull()
        assertThat(PassphraseCrypto.decrypt("x", ByteArray(3))).isNull()
    }

    @Test fun `same input encrypts differently each time`() {
        assertThat(PassphraseCrypto.encrypt("pw-12345678", plain)).isNotEqualTo(PassphraseCrypto.encrypt("pw-12345678", plain))
    }

    @Test fun `passphrase rules`() {
        assertThat(PassphraseRules.check("short", "short")).isEqualTo(PassphraseCheck.TOO_SHORT)
        assertThat(PassphraseRules.check("longenough1", "longenough2")).isEqualTo(PassphraseCheck.MISMATCH)
        assertThat(PassphraseRules.check("longenough1", "longenough1")).isEqualTo(PassphraseCheck.OK)
    }
}
