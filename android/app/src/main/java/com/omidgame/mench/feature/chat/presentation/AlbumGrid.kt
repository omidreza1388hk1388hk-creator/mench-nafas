package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import com.omidgame.mench.feature.chat.domain.Message
import com.omidgame.mench.feature.chat.domain.MessageAttachment
import com.omidgame.mench.core.network.AttachmentUrls

/**
 * Phase 2A albums. Photos sent together (the gallery picker) become consecutive IMAGE messages from one
 * sender; this groups such a run (2..10 photos, <= 60 s apart, only the first may carry a caption) so the
 * chat draws ONE grid bubble for it. Pure and client-side: the server still stores plain messages.
 */
object Albums {
    const val MAX = 10
    private const val GAP_MS = 60_000L

    class Result(val leaders: Map<String, List<Message>>, val followers: Set<String>)

    fun compute(messages: List<Message>): Result {
        val leaders = LinkedHashMap<String, List<Message>>()
        val followers = HashSet<String>()
        var run = ArrayList<Message>()
        fun flush() {
            if (run.size >= 2) {
                leaders[run.first().clientMsgId] = run.toList()
                run.drop(1).forEach { followers += it.clientMsgId }
            }
            run = ArrayList()
        }
        for (m in messages) {
            val image = m.kind == DomainMessageKind.IMAGE && m.attachment != null && m.deletedAtEpochMillis == null && m.attachment?.isViewOnce() != true
            if (!image) { flush(); continue }
            val last = run.lastOrNull()
            val continues = last != null && run.size < MAX &&
                m.body.isNullOrBlank() && m.senderId == last.senderId && m.isOwn == last.isOwn &&
                m.createdAtEpochMillis - last.createdAtEpochMillis in 0..GAP_MS
            if (!continues) flush()
            run.add(m)
        }
        flush()
        return Result(leaders, followers)
    }
}

/** The grid itself: 2 -> side by side, 3 -> one wide + two, 4 -> 2x2, more -> rows of three. */
@Composable
fun AlbumGrid(items: List<Message>, onImageClick: (MessageAttachment) -> Unit) {
    val rows: List<List<Message>> = when (items.size) {
        2 -> listOf(items)
        3 -> listOf(items.take(1), items.drop(1))
        4 -> items.chunked(2)
        else -> items.chunked(3)
    }
    Column(modifier = Modifier.widthIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { m ->
                    val attachment = m.attachment ?: return@forEach
                    val model = attachment.localContentUri ?: attachment.id?.let { AttachmentUrls.thumbnail(it) }
                    Box(Modifier.weight(1f).aspectRatio(if (row.size == 1) 2f else 1f)) {
                        AsyncImage(
                            model = model,
                            contentDescription = attachment.originalFilename,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clickable(enabled = attachment.id != null) { onImageClick(attachment) },
                        )
                    }
                }
            }
        }
    }
}
