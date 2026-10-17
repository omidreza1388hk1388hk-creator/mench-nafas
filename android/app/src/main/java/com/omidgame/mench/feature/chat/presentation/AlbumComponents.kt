package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.omidgame.mench.R
import com.omidgame.mench.core.network.AttachmentUrls
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.feature.chat.domain.AlbumItem
import com.omidgame.mench.feature.chat.domain.PickedPhoto

/** Phase 3 — the shared photo album: a grid of your photos, tap one for the full picture, reactions and caption. */

private val albumReactions = listOf("💗", "😍", "😂", "🥺", "🔥")

/** The reaction to send when [tapped] is pressed while [current] is set: pressing your own reaction again removes it. */
internal fun nextReaction(current: String?, tapped: String): String? = if (current == tapped) null else tapped

@Composable
fun AlbumDialog(
    items: List<AlbumItem>,
    onDismiss: () -> Unit,
    onAdd: (PickedPhoto, String?) -> Unit,
    onReact: (itemId: String, emoji: String?) -> Unit,
    onDelete: (itemId: String) -> Unit,
) {
    val context = LocalContext.current
    var pendingPhoto by remember { mutableStateOf<PickedPhoto?>(null) }
    var viewingId by remember { mutableStateOf<String?>(null) }
    val pick = rememberPhotoPicker(context) { pendingPhoto = it }

    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        dismissButton = { TextButton(onClick = pick) { Text(stringResource(R.string.album_add)) } },
        title = { Text("🖼️  " + stringResource(R.string.album_title)) },
        text = {
            if (items.isEmpty()) {
                EmptyHeartState(stringResource(R.string.album_empty))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewingId = item.id },
                        ) {
                            AsyncImage(
                                model = AttachmentUrls.thumbnail(item.attachmentId),
                                contentDescription = item.caption.ifBlank { null },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            val badge = listOfNotNull(item.myReaction, item.otherReaction).joinToString("")
                            if (badge.isNotEmpty()) {
                                Text(
                                    badge,
                                    fontSize = 14.sp,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    )

    pendingPhoto?.let { photo ->
        var caption by remember { mutableStateOf("") }
        MenchDialog(
            onDismissRequest = { pendingPhoto = null },
            title = { Text(stringResource(R.string.album_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(12.dp)),
                    )
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { if (it.length <= 200) caption = it },
                        label = { Text(stringResource(R.string.album_caption_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onAdd(photo, caption.trim().ifBlank { null }); pendingPhoto = null }) { Text(stringResource(R.string.album_post)) }
            },
            dismissButton = { TextButton(onClick = { pendingPhoto = null }) { Text(stringResource(R.string.us_cancel)) } },
        )
    }

    items.firstOrNull { it.id == viewingId }?.let { item ->
        Dialog(onDismissRequest = { viewingId = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = AttachmentUrls.content(item.attachmentId),
                    contentDescription = item.caption.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (item.caption.isNotBlank()) Text(item.caption, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    albumReactions.forEach { e ->
                        val mine = item.myReaction == e
                        Text(
                            e,
                            fontSize = if (mine) 30.sp else 24.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (mine) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { onReact(item.id, nextReaction(item.myReaction, e)) }
                                .padding(8.dp),
                        )
                    }
                    item.otherReaction?.let { Text(stringResource(R.string.album_their_reaction, it), color = Color.White) }
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    if (item.isMine) TextButton(onClick = { onDelete(item.id); viewingId = null }) { Text(stringResource(R.string.counters_delete), color = Color.White) } else Box {}
                    TextButton(onClick = { viewingId = null }) { Text(stringResource(R.string.us_close), color = Color.White) }
                }
            }
        }
    }
}
