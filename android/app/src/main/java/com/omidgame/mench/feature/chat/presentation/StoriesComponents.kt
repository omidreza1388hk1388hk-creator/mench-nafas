package com.omidgame.mench.feature.chat.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.feature.chat.domain.Story

/** Phase 3B — 24-hour text stories: a full-screen viewer with progress bars (tap right/left to move) and a small composer. */

/** How long each story stays on screen. */
private const val STORY_MS = 5_000

private val storyPalettes: List<Pair<Color, Color>> = listOf(
    Color(0xFFFF758C) to Color(0xFFFF7EB3),
    Color(0xFF6A11CB) to Color(0xFF2575FC),
    Color(0xFF11998E) to Color(0xFF38EF7D),
    Color(0xFFF7971E) to Color(0xFFFFD200),
    Color(0xFF232526) to Color(0xFF414345),
    Color(0xFFEE0979) to Color(0xFFFF6A00),
)

/** Gradient for a palette index; out-of-range values wrap so a future server palette never crashes an older app. */
internal fun storyBrush(palette: Int): Brush {
    val (a, b) = storyPalettes[Math.floorMod(palette, storyPalettes.size)]
    return Brush.verticalGradient(listOf(a, b))
}

/** Where to start watching: the first story you have not seen (and did not write), else the first one. */
internal fun firstUnseenIndex(stories: List<Story>): Int =
    stories.indexOfFirst { !it.seen && !it.isMine }.takeIf { it >= 0 } ?: 0

/** The index after tapping the right (+1) or left (-1) side, or null when it runs past either end (meaning: close). */
internal fun stepStory(current: Int, delta: Int, size: Int): Int? = (current + delta).takeIf { it in 0 until size }

@Composable
fun StoriesViewer(
    stories: List<Story>,
    onDismiss: () -> Unit,
    onCreate: (body: String, emoji: String?, palette: Int, photo: com.omidgame.mench.feature.chat.domain.PickedPhoto?) -> Unit,
    onView: (String) -> Unit,
    onDelete: (String) -> Unit,
    /** Phase 3B: a quick reaction to someone's story; the caller sends it into the chat. */
    onReact: (emoji: String, story: Story) -> Unit = { _, _ -> },
) {
    var composing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    if (stories.isEmpty()) {
        MenchDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { composing = true }) { Text(stringResource(R.string.stories_new)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
            title = { Text("📖  " + stringResource(R.string.stories_title)) },
            text = { Text(stringResource(R.string.stories_empty)) },
        )
    } else {
        var index by remember { mutableIntStateOf(firstUnseenIndex(stories).coerceIn(0, stories.lastIndex)) }
        val safeIndex = index.coerceIn(0, stories.lastIndex)
        val story = stories[safeIndex]
        val progress = remember(story.id) { Animatable(0f) }
        // Paused while the composer or the delete confirmation is open.
        val paused = composing || confirmDelete != null
        val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl

        LaunchedEffect(story.id) {
            if (!story.isMine && !story.seen) onView(story.id)
        }
        LaunchedEffect(story.id, paused) {
            if (!paused) {
                progress.animateTo(1f, tween((STORY_MS * (1f - progress.value)).toInt().coerceAtLeast(1), easing = LinearEasing))
                val next = stepStory(safeIndex, 1, stories.size)
                if (next == null) onDismiss() else index = next
            }
        }

        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(storyBrush(story.palette))
                    .pointerInput(story.id, stories.size, rtl) {
                        detectTapGestures { offset ->
                            val tappedEnd = if (rtl) offset.x < size.width / 2f else offset.x >= size.width / 2f
                            val next = stepStory(safeIndex, if (tappedEnd) 1 else -1, stories.size)
                            if (next == null) {
                                if (tappedEnd) onDismiss()
                            } else {
                                index = next
                            }
                        }
                    },
            ) {
                story.attachmentId?.let { id ->
                    coil.compose.AsyncImage(
                        model = com.omidgame.mench.core.network.AttachmentUrls.content(id),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // A dark veil so the white text stays readable on any photo.
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                }
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        stories.forEachIndexed { i, _ ->
                            val fill = when {
                                i < safeIndex -> 1f
                                i == safeIndex -> progress.value
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.3f)),
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(fill).height(3.dp).background(Color.White))
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (story.emoji.isNotBlank()) Text(story.emoji, fontSize = 64.sp)
                            Text(
                                text = story.body,
                                color = Color.White,
                                fontSize = 26.sp,
                                lineHeight = 36.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (story.isMine) {
                            Text("👁 " + stringResource(R.string.stories_views, story.views), color = Color.White)
                            TextButton(onClick = { confirmDelete = story.id }) { Text(stringResource(R.string.counters_delete), color = Color.White) }
                        } else {
                            Row {
                                listOf("💗", "😍", "😂", "🥺", "🔥").forEach { e ->
                                    TextButton(onClick = { onReact(e, story) }) { Text(e, fontSize = 22.sp) }
                                }
                            }
                        }
                        TextButton(onClick = { composing = true }) { Text("＋ " + stringResource(R.string.stories_new), color = Color.White) }
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        MenchDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.stories_delete_confirm)) },
            confirmButton = { TextButton(onClick = { onDelete(id); confirmDelete = null }) { Text(stringResource(R.string.counters_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.us_cancel)) } },
        )
    }

    if (composing) {
        var body by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("💗") }
        var palette by remember { mutableIntStateOf(0) }
        var storyPhoto by remember { mutableStateOf<com.omidgame.mench.feature.chat.domain.PickedPhoto?>(null) }
        val pickStoryPhoto = rememberPhotoPicker(androidx.compose.ui.platform.LocalContext.current) { storyPhoto = it }
        MenchDialog(
            onDismissRequest = { composing = false },
            title = { Text(stringResource(R.string.stories_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(storyBrush(palette)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (emoji + " " + body).trim().ifBlank { "…" },
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = body,
                        onValueChange = { if (it.length <= 280) body = it },
                        label = { Text(stringResource(R.string.stories_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    TextButton(onClick = pickStoryPhoto) {
                        Text(if (storyPhoto == null) "📷 " + stringResource(R.string.stories_add_photo) else "✅ " + stringResource(R.string.letters_photo_attached))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("💗", "🌙", "☀️", "🎉", "🥺").forEach { e ->
                            FilterChip(selected = emoji == e, onClick = { emoji = if (emoji == e) "" else e }, label = { Text(e) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        storyPalettes.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (i == palette) 34.dp else 28.dp)
                                    .clip(CircleShape)
                                    .background(storyBrush(i))
                                    .clickable { palette = i },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = body.isNotBlank(),
                    onClick = { onCreate(body.trim(), emoji.ifBlank { null }, palette, storyPhoto); composing = false },
                ) { Text(stringResource(R.string.stories_post)) }
            },
            dismissButton = { TextButton(onClick = { composing = false }) { Text(stringResource(R.string.us_cancel)) } },
        )
    }
}
