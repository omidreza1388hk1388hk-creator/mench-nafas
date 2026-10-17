package com.omidgame.mench.core.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal on-device crash reporter: writes every uncaught exception to
 * files/crashes/crash-<timestamp>.txt, then hands off to the previous handler
 * (so the system crash dialog / process death still happens). Keeps the 10 newest
 * files. No network involved; read them with [readLatest] or pull from the device.
 */
object CrashLogger {
    private const val DIR = "crashes"
    private const val KEEP = 10

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = File(appContext.filesDir, DIR).apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(dir, "crash-$stamp.txt").writeText("thread=${thread.name}\n$sw")
                dir.listFiles { f -> f.name.endsWith(".txt") }?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
            } catch (_: Throwable) {
                // never let the reporter itself mask the original crash
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Sends crashes recorded by earlier runs to the server (category "crash"). Best effort: any failure
     * leaves the file in place for the next launch; sent files are renamed to *.sent so they are never
     * uploaded twice. At most 3 per launch to keep it cheap.
     */
    suspend fun uploadPending(context: Context, api: com.omidgame.mench.core.network.ProductApi, appVersion: String) {
        val dir = File(context.filesDir, DIR)
        val pending = dir.listFiles { f -> f.name.endsWith(".txt") }?.sortedBy { it.lastModified() }?.takeLast(3).orEmpty()
        for (file in pending) {
            try {
                val text = file.readText().take(60_000)
                api.feedback(
                    com.omidgame.mench.core.network.FeedbackBody(
                        category = "crash",
                        message = text.lineSequence().drop(1).firstOrNull { it.isNotBlank() }?.take(300) ?: "crash",
                        appVersion = appVersion,
                        deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}",
                        logs = text,
                    ),
                )
                file.renameTo(File(dir, file.name.removeSuffix(".txt") + ".sent"))
            } catch (_: Throwable) {
                break // offline or not signed in yet: try again next launch
            }
        }
    }

    fun readLatest(context: Context): String? =
        File(context.filesDir, DIR).listFiles { f -> f.name.endsWith(".txt") }?.maxByOrNull { it.lastModified() }?.readText()
}
