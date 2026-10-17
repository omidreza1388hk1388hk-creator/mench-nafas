package com.omidgame.mench.core.backup

import android.content.Context
import com.omidgame.mench.core.crypto.E2eeManager
import com.omidgame.mench.core.database.ConversationDao
import com.omidgame.mench.core.database.MessageDao
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class RestoreResult { OK, WRONG_PASSPHRASE_OR_CORRUPT, NOT_A_BACKUP }

/**
 * Phase 4C — passphrase-encrypted backup / device migration.
 *
 * File layout: "MENCHBAK1" | salt(16) | iv(12) | AES-256-GCM( gzip( JSON ) ).
 * The key is PBKDF2-HMAC-SHA256 (210,000 iterations) of the passphrase, so the file is useless without it.
 * The JSON holds: the end-to-end-encryption identity key (so a new phone can read secure history), this
 * app's local settings (chat themes, locks, secure-chat list, sounds, drafts...), and a readable archive of
 * the messages stored on this phone. Restoring puts back the key + settings; the messages stay in the
 * archive (the server is still the source of truth for chat history).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
) {
    companion object {
        private val MAGIC = "MENCHBAK1".toByteArray()
        private const val ITERATIONS = 210_000

        /** SharedPreferences files that make up "my settings on this phone" (all plain, none holds a secret). */
        private val PREF_FILES = listOf(
            "chat_wallpaper", "chat_bubble_skins", "mench_locked_chats", "mench_e2ee_chats", "mench_e2ee_peers",
            "mench_ui_sounds", "mench_opened_surprises", "mench_opened_view_once", "drafts",
        )
    }

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    private fun dumpPrefs(): JSONObject {
        val root = JSONObject()
        PREF_FILES.forEach { name ->
            val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JSONObject()
            sp.all.forEach { (k, v) ->
                val e = JSONObject()
                when (v) {
                    is String -> e.put("t", "s").put("v", v)
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is Set<*> -> e.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                    else -> return@forEach
                }
                o.put(k, e)
            }
            root.put(name, o)
        }
        return root
    }

    private fun restorePrefs(root: JSONObject) {
        PREF_FILES.forEach { name ->
            val o = root.optJSONObject(name) ?: return@forEach
            val ed = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            o.keys().forEach { k ->
                val e = o.getJSONObject(k)
                when (e.getString("t")) {
                    "s" -> ed.putString(k, e.getString("v"))
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "ss" -> ed.putStringSet(k, e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                }
            }
            ed.apply()
        }
    }

    suspend fun write(passphrase: String, out: OutputStream) {
        val json = JSONObject().put("format", 1).put("createdAt", System.currentTimeMillis())
        E2eeManager.exportIdentity()?.let { (priv, pub) -> json.put("e2eeIdentity", JSONObject().put("priv", priv).put("pub", pub)) }
        json.put("prefs", dumpPrefs())
        val archive = JSONArray()
        conversationDao.getAllOnce().forEach { c ->
            messageDao.getAllForConversationOnce(c.id).forEach { m ->
                archive.put(
                    JSONObject().put("conversationId", m.conversationId).put("sender", m.senderId).put("kind", m.kind)
                        .put("body", m.body ?: JSONObject.NULL).put("at", m.createdAtEpochMillis).put("serverId", m.serverId ?: JSONObject.NULL),
                )
            }
        }
        json.put("messages", archive)

        val raw = java.io.ByteArrayOutputStream()
        GZIPOutputStream(raw).use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv)) }
        out.write(MAGIC); out.write(salt); out.write(iv); out.write(cipher.doFinal(raw.toByteArray()))
        out.flush()
        // Settings Phase 1B: remember when the last good backup was written (drives the Security hub and reminders).
        BackupStatusStore.recordSuccess(context)
    }

    fun restore(passphrase: String, input: InputStream): RestoreResult {
        val all = input.readBytes()
        if (all.size < MAGIC.size + 28 || !all.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return RestoreResult.NOT_A_BACKUP
        val salt = all.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = all.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val plain = runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv)) }
            GZIPInputStream(c.doFinal(all, MAGIC.size + 28, all.size - MAGIC.size - 28).inputStream()).readBytes().toString(Charsets.UTF_8)
        }.getOrNull() ?: return RestoreResult.WRONG_PASSPHRASE_OR_CORRUPT
        val json = runCatching { JSONObject(plain) }.getOrNull() ?: return RestoreResult.WRONG_PASSPHRASE_OR_CORRUPT
        json.optJSONObject("e2eeIdentity")?.let { E2eeManager.importIdentity(it.getString("priv"), it.getString("pub")) }
        json.optJSONObject("prefs")?.let(::restorePrefs)
        return RestoreResult.OK
    }
}
