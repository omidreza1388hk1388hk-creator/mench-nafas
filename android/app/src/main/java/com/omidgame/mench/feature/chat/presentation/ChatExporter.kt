package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.core.ui.theme.MenchCalendar
import android.content.Context
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import androidx.core.content.FileProvider
import com.omidgame.mench.R
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import com.omidgame.mench.feature.chat.domain.Message
import java.io.File
import java.util.Date

/**
 * Turns a conversation into a plain-text file ("[۱۴۰۴/۰۷/۱۲ ۱۴:۰۵] نام: متن", Jalali dates) and
 * hands it to the system share sheet, so you can save it to Files, Drive, email… The text
 * is built from the on-phone copy, so it contains whatever this phone has synced.
 * Attachments appear as a short label (the files themselves are not included).
 */
object ChatExporter {

    fun buildText(context: Context, messages: List<Message>, myName: String, otherName: String): String {
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm", MenchCalendar.locale())
        return buildString {
            messages.filter { it.deletedAtEpochMillis == null }.forEach { message ->
                val who = if (message.isOwn) myName else otherName
                val content = when (message.kind) {
                    DomainMessageKind.TEXT -> message.body.orEmpty()
                    DomainMessageKind.IMAGE -> context.getString(R.string.export_image) + message.body?.let { " $it" }.orEmpty()
                    DomainMessageKind.VIDEO -> context.getString(R.string.export_video) + message.body?.let { " $it" }.orEmpty()
                    DomainMessageKind.AUDIO -> context.getString(R.string.export_voice)
                    DomainMessageKind.FILE -> context.getString(R.string.export_file) + (message.attachment?.originalFilename?.let { " $it" } ?: "")
                }
                append('[').append(format.format(Date(message.createdAtEpochMillis))).append("] ")
                append(who).append(": ").append(content).append('\n')
            }
        }
    }

    fun share(context: Context, fileLabel: String, text: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = fileLabel.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(40).ifBlank { "chat" }
        val file = File(dir, "mench-$safe.txt").apply { writeText(text, Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.export_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
