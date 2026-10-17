package com.omidgame.mench.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** One day of network usage in bytes, split by connection type and by traffic class. */
data class DayUsage(
    val day: String,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val mediaBytes: Long,
)

/**
 * Phase 1D.5 data accounting. An OkHttp interceptor adds up request/response sizes per day, per
 * connection type (Wi-Fi vs mobile/roaming) and per traffic class (media vs API). Counters live in
 * SharedPreferences (one small JSON per day) and are pruned after 90 days. It only COUNTS bytes:
 * no URLs, no content, no identifiers are stored.
 *
 * Sizes use Content-Length when the server/client provides it, so chunked bodies of unknown length
 * are under-counted; the dashboard labels the numbers as estimates.
 */
private val CONVERSATION_PATH = Regex("/conversations/([0-9a-fA-F-]{36})")

@Singleton
class UsageTracker @Inject constructor(
    @ApplicationContext context: Context,
    private val networkMonitor: NetworkMonitor,
) : Interceptor {
    private val prefs = context.getSharedPreferences("mench_usage", Context.MODE_PRIVATE)
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        runCatching {
            val up = request.body?.contentLength()?.takeIf { it > 0 } ?: 0L
            val down = response.body?.contentLength()?.takeIf { it > 0 } ?: 0L
            val path = request.url.encodedPath
            val isMedia = path.contains("/attachments") || path.contains("/avatar") || path.contains("/files")
            record(up, down, isMedia, CONVERSATION_PATH.find(path)?.groupValues?.get(1))
        }
        return response
    }

    @Synchronized
    private fun record(up: Long, down: Long, isMedia: Boolean, conversationId: String?) {
        if (up + down <= 0) return
        val key = "d_" + fmt.format(Date())
        val j = JSONObject(prefs.getString(key, "{}") ?: "{}")
        val kind = networkMonitor.kind.value
        val net = if (kind == NetworkKind.WIFI) "wifi" else "mobile"
        fun add(name: String, v: Long) { j.put(name, j.optLong(name, 0L) + v) }
        add(net, up + down)
        add("up", up)
        add("down", down)
        if (isMedia) add("media", up + down)
        // Phase 1 completion: per-chat attribution. Only the conversation id from the URL path is kept, never content.
        if (conversationId != null) add("c_$conversationId", up + down)
        prefs.edit().putString(key, j.toString()).apply()
        if (prefs.getLong("pruned_at", 0L) < System.currentTimeMillis() - 86_400_000L) prune()
    }

    private fun prune() {
        val cutoff = "d_" + fmt.format(Date(System.currentTimeMillis() - 90L * 86_400_000L))
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("d_") && it < cutoff }.forEach { edit.remove(it) }
        edit.putLong("pruned_at", System.currentTimeMillis()).apply()
    }

    /** Oldest-first list for the last [days] days, including zero days so charts have no gaps. */
    fun lastDays(days: Int): List<DayUsage> = (days - 1 downTo 0).map { ago ->
        val day = fmt.format(Date(System.currentTimeMillis() - ago * 86_400_000L))
        val j = JSONObject(prefs.getString("d_$day", "{}") ?: "{}")
        DayUsage(day, j.optLong("wifi"), j.optLong("mobile"), j.optLong("up"), j.optLong("down"), j.optLong("media"))
    }

    /** Total bytes per conversation over the last [days] days, largest first. Only traffic whose URL names a conversation is attributed. */
    fun byChat(days: Int): List<Pair<String, Long>> {
        val totals = HashMap<String, Long>()
        for (ago in 0 until days) {
            val day = fmt.format(Date(System.currentTimeMillis() - ago * 86_400_000L))
            val j = JSONObject(prefs.getString("d_$day", "{}") ?: "{}")
            val keys = j.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k.startsWith("c_")) totals[k.removePrefix("c_")] = (totals[k.removePrefix("c_")] ?: 0L) + j.optLong(k)
            }
        }
        return totals.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    fun reset() {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("d_") }.forEach { edit.remove(it) }
        edit.apply()
    }
}
