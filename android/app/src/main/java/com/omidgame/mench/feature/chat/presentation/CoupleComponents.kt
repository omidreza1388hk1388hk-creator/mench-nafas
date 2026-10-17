package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.omidgame.mench.core.ui.components.MenchDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.feature.chat.domain.Question
import com.omidgame.mench.feature.chat.domain.Wish

/** "سؤال امروز": one question a day; the partner's answer stays hidden until you've answered too. */
@Composable
fun QuestionDialog(
    question: Question?,
    onDismiss: () -> Unit,
    onAnswer: (String) -> Unit,
) {
    var draft by remember(question?.date, question?.myAnswer) { mutableStateOf(question?.myAnswer.orEmpty()) }
    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        title = { Text("❓  " + stringResource(R.string.question_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (question == null) {
                    Text(stringResource(R.string.question_loading))
                } else {
                    if (question.streak > 0) {
                        Text(
                            stringResource(R.string.question_streak, question.streak),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (question.bestStreak > question.streak) {
                            Text(
                                stringResource(R.string.question_best_streak, question.bestStreak),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Text(question.question, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 500) draft = it },
                        label = { Text(stringResource(R.string.question_your_answer)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onAnswer(draft.trim()) },
                        enabled = draft.isNotBlank() && draft.trim() != question.myAnswer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (question.myAnswer == null) R.string.question_send else R.string.question_update))
                    }
                    when {
                        question.partnerAnswer != null -> {
                            Text(stringResource(R.string.question_partner_answer), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("💗 " + question.partnerAnswer)
                        }
                        question.partnerAnswered -> Text(stringResource(R.string.question_partner_answered_hidden))
                        else -> Text(stringResource(R.string.question_partner_waiting))
                    }
                }
            }
        },
    )
}

/** "لیست آرزوها": things to do together; tap the box when you've done one. */
@Composable
fun WishlistDialog(
    wishes: List<Wish>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onToggle: (Wish) -> Unit,
    onDelete: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        title = { Text("✨  " + stringResource(R.string.wishlist_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 100) title = it },
                        placeholder = { Text(stringResource(R.string.wishlist_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = title.isNotBlank(),
                        onClick = { onAdd(title.trim()); title = "" },
                    ) { Text(stringResource(R.string.wishlist_add)) }
                }
                if (wishes.isEmpty()) {
                    Text(stringResource(R.string.wishlist_empty))
                } else {
                    LazyColumn {
                        items(wishes, key = { it.id }) { wish ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = wish.done, onCheckedChange = { onToggle(wish) })
                                Text(
                                    text = wish.title,
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (wish.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                )
                                if (wish.isMine) {
                                    IconButton(onClick = { onDelete(wish.id) }) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.memories_delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

/** The pinned-message banner shown under the top bar; tap to jump, X to unpin. */
@Composable
fun PinnedBanner(
    text: String,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
            Text("📌", modifier = Modifier.padding(end = 8.dp))
            Text(
                text = text,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onUnpin) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pin_remove)) }
        }
    }
}

/** Supplied once by ChatScreen; lets any bubble ask the server for a link preview without threading a lambda through every layer. */
val LocalLinkPreviewLoader = androidx.compose.runtime.staticCompositionLocalOf<suspend (String) -> com.omidgame.mench.feature.chat.domain.LinkPreview?> { { null } }

private val URL_REGEX = Regex("""https?://[^\s<>"]+""")

/** First http(s) link in [text], trailing punctuation trimmed, or null. */
fun firstUrl(text: String?): String? =
    text?.let { URL_REGEX.find(it)?.value?.trimEnd('.', ',', ')', '!', '?', ';', ':', '،', '؛') }

/** In-memory cache so scrolling a bubble in and out of view doesn't refetch (the server also caches). */
private object LinkPreviewMemory {
    val cache = java.util.concurrent.ConcurrentHashMap<String, com.omidgame.mench.feature.chat.domain.LinkPreview?>()
}

/** A small card (site, title, description) under a message that contains a link. Tapping opens the link. */
@Composable
fun LinkPreviewCard(url: String, modifier: Modifier = Modifier) {
    val loader = LocalLinkPreviewLoader.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val preview by androidx.compose.runtime.produceState<com.omidgame.mench.feature.chat.domain.LinkPreview?>(
        initialValue = LinkPreviewMemory.cache[url],
        key1 = url,
    ) {
        if (!LinkPreviewMemory.cache.containsKey(url)) {
            val loaded = loader(url)
            LinkPreviewMemory.cache[url] = loaded
            value = loaded
        }
    }
    val p = preview ?: return
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        onClick = { runCatching { uriHandler.openUri(url) } },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            p.siteName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
            p.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2) }
            p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 3) }
        }
    }
}
