package com.omidgame.mench.core.media

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a picked (SAF/Photo-Picker) Uri's bytes into app-private storage
 * immediately when the user picks a file to send. This matters for two
 * separate reasons, not one:
 *  1. The Uri grant from a picker is typically one-shot / process-scoped —
 *     it can stop resolving well before the durable Outbox gets around to
 *     actually uploading (which might be minutes or hours later if the
 *     device was offline). Reading it lazily inside OutboxSyncWorker would
 *     intermittently fail for reasons that have nothing to do with the
 *     network.
 *  2. Copying immediately is also what makes the message's local preview
 *     (a thumbnail, a filename) available instantly for optimistic
 *     rendering, before any upload has even started.
 */
@Singleton
class AttachmentCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheDir: File by lazy {
        File(context.filesDir, "attachment_cache").apply { mkdirs() }
    }

    suspend fun copyToCache(sourceUri: String, suggestedExtension: String?): String = withContext(Dispatchers.IO) {
        val destFile = File(cacheDir, "${UUID.randomUUID()}${suggestedExtension.orEmpty()}")
        context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not open input stream for $sourceUri")
        destFile.absolutePath
    }

    fun sizeOf(localPath: String): Long = File(localPath).length()

    fun readBytes(localPath: String): ByteArray = File(localPath).readBytes()

    fun delete(localPath: String) {
        File(localPath).delete()
    }

    /** Real bytes-on-disk, walked fresh every call — deliberately not cached, since the whole point is showing the user an accurate, current number on the Data & Storage screen. */
    suspend fun totalCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Deletes every locally cached attachment file. This only removes the
     * device-local copy used for instant preview/offline viewing — it
     * never touches server-side data (spec section 42: clearing cache
     * must not delete server-side data), so previously-viewed media is
     * simply re-downloaded from the attachment endpoint next time it's
     * opened.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** Phase 6 diagnostics "Storage" check — a real write+read+delete round trip against the same directory attachments actually use, not just a permission-flag check. */
    fun verifyWritable(): Boolean = try {
        val probe = File(cacheDir, "diagnostics_probe_${UUID.randomUUID()}.tmp")
        probe.writeText("ok")
        val ok = probe.readText() == "ok"
        probe.delete()
        ok
    } catch (e: Exception) {
        false
    }
}
