package com.omidgame.mench.core.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.omidgame.mench.core.network.ChatApi
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Phase 4C — end-to-end encryption for 1:1 text messages (opt-in per chat).
 *
 * Every account has one EC P-256 identity key pair. The PRIVATE key lives only on the phone, inside
 * EncryptedSharedPreferences (Android Keystore-backed); the public key is published to the server.
 *
 * A secure message body is `e2ee:v2:<ct>.<wrapForPeer>.<wrapForSelf>` (url-safe base64 parts):
 *   - the text is encrypted once with a random AES-256-GCM key K  -> <ct>
 *   - K is wrapped for each of the two people: ephemeral ECDH with their public key -> HKDF-SHA256 ->
 *     AES-256-GCM(K). Wrapping for yourself too is what lets you re-read your own history.
 * The server stores and relays only this opaque string.
 *
 * Moving to a new phone: the identity key is part of the passphrase-encrypted backup (see BackupManager).
 */
object E2eeManager {
    private const val PREFIX = "e2ee:v2:"
    private const val PREFS = "mench_e2ee_keys"
    private const val CHATS = "mench_e2ee_chats"
    private const val KEY_PRIV = "priv"
    private const val KEY_PUB = "pub"
    private const val INFO = "mench-e2ee-v2"
    private const val LOCKED_TEXT = "🔒 پیام رمزشده — روی این گوشی قابل‌خوندن نیست"

    private val rnd = SecureRandom()
    private val b64e = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()

    @Volatile private var appContext: Context? = null
    @Volatile private var secure: SharedPreferences? = null

    fun init(context: Context) { appContext = context.applicationContext }

    fun isE2ee(body: String?): Boolean = body != null && body.startsWith(PREFIX)

    private fun ctx(): Context = appContext ?: error("E2eeManager.init(context) was not called")

    @Synchronized
    private fun keyPrefs(): SharedPreferences {
        secure?.let { return it }
        val c = ctx()
        val master = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val p = EncryptedSharedPreferences.create(
            c, PREFS, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        secure = p
        return p
    }

    private fun chatPrefs() = ctx().getSharedPreferences(CHATS, Context.MODE_PRIVATE)

    // ---------- identity ----------

    /** Creates the identity key pair on first use. Returns the public key (X.509, standard base64). */
    @Synchronized
    fun ensureIdentity(): String {
        val p = keyPrefs()
        p.getString(KEY_PUB, null)?.let { return it }
        val kp: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val pub = Base64.getEncoder().encodeToString(kp.public.encoded)
        p.edit().putString(KEY_PRIV, Base64.getEncoder().encodeToString(kp.private.encoded)).putString(KEY_PUB, pub).apply()
        return pub
    }

    fun publicKey(): String? = keyPrefs().getString(KEY_PUB, null)

    /** For the encrypted backup: (private PKCS8, public X.509), both standard base64. */
    fun exportIdentity(): Pair<String, String>? {
        val p = keyPrefs()
        val priv = p.getString(KEY_PRIV, null) ?: return null
        val pub = p.getString(KEY_PUB, null) ?: return null
        return priv to pub
    }

    /** Restores an identity from a backup. Returns false if the pair is not a valid, matching key pair. */
    fun importIdentity(privB64: String, pubB64: String): Boolean = runCatching {
        val kf = KeyFactory.getInstance("EC")
        kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privB64)))
        kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pubB64)))
        keyPrefs().edit().putString(KEY_PRIV, privB64).putString(KEY_PUB, pubB64).apply()
        true
    }.getOrDefault(false)

    /** Uploads the public key (idempotent). Best effort. */
    suspend fun publish(api: ChatApi) {
        runCatching { api.setE2eeKey(com.omidgame.mench.core.network.E2eeKeyBody(ensureIdentity())) }
    }

    // ---------- per-chat switch ----------

    fun isSecureChat(conversationId: String): Boolean = chatPrefs().contains(conversationId)
    fun peerOf(conversationId: String): String? = chatPrefs().getString(conversationId, null)
    fun setSecureChat(conversationId: String, peerUserId: String?) {
        chatPrefs().edit().apply { if (peerUserId == null) remove(conversationId) else putString(conversationId, peerUserId) }.apply()
    }

    /** The partner's last fetched public key, without any network call (read-only; used for the security code). */
    fun cachedPeerKey(userId: String): String? =
        ctx().getSharedPreferences("mench_e2ee_peers", Context.MODE_PRIVATE).getString(userId, null)

    /** Fetches (and remembers) the partner's public key; null if they have not published one. */
    suspend fun peerKey(api: ChatApi, userId: String): String? {
        val cache = ctx().getSharedPreferences("mench_e2ee_peers", Context.MODE_PRIVATE)
        runCatching { api.getE2eeKey(userId).publicKey }.getOrNull()?.let { fresh ->
            cache.edit().putString(userId, fresh).apply()
            return fresh
        }
        return cache.getString(userId, null)
    }

    // ---------- crypto ----------

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    private fun kek(shared: ByteArray): ByteArray {
        val prk = hmac(ByteArray(32), shared)
        return hmac(prk, INFO.toByteArray() + byteArrayOf(1))
    }

    private fun ecdh(priv: PrivateKey, pub: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run { init(priv); doPhase(pub, true); generateSecret() }

    private fun gcmSeal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(INFO.toByteArray())
        return iv + c.doFinal(plain)
    }

    private fun gcmOpen(key: ByteArray, blob: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        c.updateAAD(INFO.toByteArray())
        c.doFinal(blob, 12, blob.size - 12)
    }.getOrNull()

    private fun wrap(contentKey: ByteArray, recipientPubB64: String): ByteArray {
        val recipient = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(recipientPubB64)))
        val eph = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val ephPub = eph.public.encoded // X.509, always 91 bytes for P-256
        return ephPub + gcmSeal(kek(ecdh(eph.private, recipient)), contentKey)
    }

    private fun unwrap(wrapped: ByteArray, myPriv: PrivateKey): ByteArray? = runCatching {
        if (wrapped.size <= 91) return null
        val ephPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(wrapped.copyOfRange(0, 91)))
        gcmOpen(kek(ecdh(myPriv, ephPub)), wrapped.copyOfRange(91, wrapped.size))
    }.getOrNull()

    /** Encrypts [plaintext] for the partner and for yourself. Throws if a key is malformed. */
    fun encrypt(plaintext: String, peerPubB64: String): String {
        val contentKey = ByteArray(32).also(rnd::nextBytes)
        val ct = gcmSeal(contentKey, plaintext.toByteArray(Charsets.UTF_8))
        val forPeer = wrap(contentKey, peerPubB64)
        val forSelf = wrap(contentKey, ensureIdentity())
        return PREFIX + b64e.encodeToString(ct) + "." + b64e.encodeToString(forPeer) + "." + b64e.encodeToString(forSelf)
    }

    /** Plain bodies come back unchanged; secure bodies are decrypted, or replaced by a lock notice. */
    fun decryptBody(body: String?): String? {
        if (body == null || !isE2ee(body)) return body
        return runCatching {
            val parts = body.removePrefix(PREFIX).split('.')
            if (parts.size != 3) return LOCKED_TEXT
            val priv = KeyFactory.getInstance("EC").generatePrivate(
                PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyPrefs().getString(KEY_PRIV, null) ?: return LOCKED_TEXT)),
            )
            val ct = b64d.decode(parts[0])
            val contentKey = unwrap(b64d.decode(parts[1]), priv) ?: unwrap(b64d.decode(parts[2]), priv) ?: return LOCKED_TEXT
            gcmOpen(contentKey, ct)?.toString(Charsets.UTF_8) ?: LOCKED_TEXT
        }.getOrDefault(LOCKED_TEXT)
    }

    /**
     * What to put on the wire for [plaintext] sent into [conversationId]: the encrypted form in a secure
     * chat, the text itself otherwise. Returns null when the chat is secure but the partner's key is not
     * available — the caller must NOT fall back to plaintext in that case.
     */
    suspend fun prepareOutgoing(api: ChatApi, conversationId: String, plaintext: String): String? {
        val peer = peerOf(conversationId) ?: return plaintext
        val peerKey = peerKey(api, peer) ?: return null
        // Settings Phase 1B: a partner key that differs from the one we trusted blocks sending until the user
        // acknowledges it (null = "do not send": the outbox retries, nothing is ever sent as plaintext).
        if (!PeerKeyTrust.check(ctx(), peer, peerKey)) return null
        return runCatching { encrypt(plaintext, peerKey) }.getOrNull()
    }
}
