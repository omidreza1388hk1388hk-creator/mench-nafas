package com.omidgame.mench.core.accounts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.sqlite.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One MENCH account that has been used on this device. [slot] is a local id, not the server user id. */
data class AccountEntry(val slot: String, val displayName: String?, val username: String?, val userId: String?)

/**
 * Multi-account support (Phase 1D.1).
 *
 * Every account lives in its own "slot". The slot chosen at process start decides which files the
 * whole app sees: [AccountContext] (installed in MenchApplication.attachBaseContext) renames
 * SharedPreferences and databases and moves files/cache into a per-slot folder, so tokens, the Room
 * database, drafts, settings, notification policy and caches are isolated per account with NO
 * changes to the rest of the code. The first account keeps the slot "default" and its original file
 * names, so existing installs keep all their data.
 *
 * Switching accounts = write the new slot, then restart the process (the simplest way to rebuild
 * every Hilt singleton against the other account's database).
 */
object AccountRegistry {
    const val DEFAULT_SLOT = "default"
    private const val PREFS = "mench_accounts"
    private const val KEY_ACTIVE = "active"
    private const val KEY_LIST = "list"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun activeSlot(c: Context): String = prefs(c).getString(KEY_ACTIVE, DEFAULT_SLOT) ?: DEFAULT_SLOT

    fun list(c: Context): List<AccountEntry> {
        val raw = prefs(c).getString(KEY_LIST, null)
        val parsed = runCatching {
            val arr = JSONArray(raw ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AccountEntry(o.getString("slot"), o.optString("name").ifBlank { null }, o.optString("username").ifBlank { null }, o.optString("userId").ifBlank { null })
            }
        }.getOrDefault(emptyList())
        // The original account always exists, even before anything was recorded.
        return if (parsed.none { it.slot == DEFAULT_SLOT }) listOf(AccountEntry(DEFAULT_SLOT, null, null, null)) + parsed else parsed
    }

    private fun save(c: Context, accounts: List<AccountEntry>) {
        val arr = JSONArray()
        accounts.forEach {
            arr.put(JSONObject().put("slot", it.slot).put("name", it.displayName ?: "").put("username", it.username ?: "").put("userId", it.userId ?: ""))
        }
        prefs(c).edit().putString(KEY_LIST, arr.toString()).commit()
    }

    /** Remembers the signed-in person's name for the account switcher. */
    fun updateActive(c: Context, displayName: String?, username: String?, userId: String?) {
        val slot = activeSlot(c)
        val all = list(c).toMutableList()
        val i = all.indexOfFirst { it.slot == slot }
        val entry = AccountEntry(slot, displayName, username, userId)
        if (i >= 0) all[i] = entry else all += entry
        save(c, all)
    }

    /** Creates an empty slot for "Add account" and returns its id. */
    fun createSlot(c: Context): String {
        val slot = "a" + UUID.randomUUID().toString().replace("-", "").take(10)
        save(c, list(c) + AccountEntry(slot, null, null, null))
        return slot
    }

    /** Removes an INACTIVE account and deletes everything stored for it on this device. */
    fun removeSlot(c: Context, slot: String): Boolean {
        if (slot == DEFAULT_SLOT || slot == activeSlot(c)) return false
        save(c, list(c).filterNot { it.slot == slot })
        val base = c.applicationInfo.dataDir
        File(base, "shared_prefs").listFiles { f -> f.name.startsWith("${slot}__") }?.forEach { it.delete() }
        File(base, "databases").listFiles { f -> f.name.startsWith("${slot}__") }?.forEach { it.delete() }
        // Absolute paths: c.filesDir / c.cacheDir already point inside the ACTIVE account's folder.
        File(base, "files/acct_$slot").deleteRecursively()
        File(base, "cache/acct_$slot").deleteRecursively()
        return true
    }

    /** Persists the choice and restarts the app into that account. Does not return. */
    fun switchAndRestart(c: Context, slot: String) {
        prefs(c).edit().putString(KEY_ACTIVE, slot).commit()
        val launch = c.packageManager.getLaunchIntentForPackage(c.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pi = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 250, pi)
        android.os.Process.killProcess(android.os.Process.myPid())
        Runtime.getRuntime().exit(0)
    }
}

/**
 * Wraps the application base context so each account slot gets its own preferences, databases and
 * file/cache folders. The slot "default" is a pure pass-through. The account registry itself and the
 * app-lock settings (a device-level choice) are exempt and shared by all accounts.
 */
class AccountContext(base: Context, private val slot: String) : ContextWrapper(base) {
    private val exempt = setOf("mench_accounts", "mench_secure_applock")

    private fun slotName(n: String) = if (slot == AccountRegistry.DEFAULT_SLOT || n in exempt) n else "${slot}__$n"
    private fun sub(dir: File): File = if (slot == AccountRegistry.DEFAULT_SLOT) dir else File(dir, "acct_$slot").apply { mkdirs() }

    override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(slotName(name), mode)
    override fun getDatabasePath(name: String): File = super.getDatabasePath(slotName(name))
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        super.openOrCreateDatabase(slotName(name), mode, factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        super.openOrCreateDatabase(slotName(name), mode, factory, errorHandler)
    override fun deleteDatabase(name: String): Boolean = super.deleteDatabase(slotName(name))
    override fun getFilesDir(): File = sub(super.getFilesDir())
    override fun getCacheDir(): File = sub(super.getCacheDir())
}
