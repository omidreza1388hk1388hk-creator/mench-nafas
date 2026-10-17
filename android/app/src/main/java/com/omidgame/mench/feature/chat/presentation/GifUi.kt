package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.omidgame.mench.R
import com.omidgame.mench.core.network.GifUrls

/**
 * GIF tab of the media panel. Opens on romantic topics; typing works in Persian or English.
 * [onPick] gets the GIF id and its size so the caller can build the message body ([RichBody.encodeGif]).
 */
@Composable
fun GifPicker(onPick: (id: String, width: Int, height: Int) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: GifViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val persian = isPersianUi()
    val gridState = rememberLazyGridState()

    // Load the next page when the user scrolls near the end of what is loaded.
    LaunchedEffect(gridState, state.items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (state.items.isNotEmpty() && last >= state.items.size - 6) viewModel.loadMore() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TextField(
            value = state.typed,
            onValueChange = viewModel::onTyped,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.gif_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(GifQuery.topics, key = { it.query }) { topic ->
                val selected = state.topic == topic
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { viewModel.onTopic(topic) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (persian) topic.labelFa else topic.labelEn,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.error == GifError.NOT_CONFIGURED -> CenterMessage(stringResource(R.string.gif_not_configured), null)
                state.error == GifError.UNAVAILABLE -> CenterMessage(stringResource(R.string.gif_unavailable), viewModel::retry)
                state.loading && state.items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.items.isEmpty() -> CenterMessage(stringResource(R.string.gif_no_results), null)
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.items, key = { it.id }) { gif ->
                        AsyncImage(
                            model = GifUrls.preview(gif.id),
                            contentDescription = gif.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onPick(gif.id, gif.width, gif.height) },
                        )
                    }
                }
            }
        }
        // Giphy's terms require attribution wherever its content is shown.
        Text(
            text = stringResource(R.string.gif_powered_by),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun CenterMessage(text: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.gif_retry)) }
    }
}

/** A GIF inside a chat bubble: animated, at its real aspect ratio (clamped), loaded through our own server. */
@Composable
fun GifBubbleImage(id: String, widthPx: Int, heightPx: Int, modifier: Modifier = Modifier) {
    val ratio = (widthPx.toFloat() / heightPx.toFloat()).coerceIn(0.5f, 2.2f)
    AsyncImage(
        model = GifUrls.full(id),
        contentDescription = stringResource(R.string.preview_gif),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .width(220.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/** Emoji | Stickers | GIF tabs under the composer. Replaces the bare emoji picker. */
@Composable
fun MediaPanel(
    onEmoji: (String) -> Unit,
    onRich: (body: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(R.string.media_tab_emoji, R.string.media_tab_stickers, R.string.media_tab_gif).forEachIndexed { index, label ->
                val selected = tab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { tab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            when (tab) {
                0 -> EmojiPicker(onPick = onEmoji)
                1 -> StickerTray(onPick = onRich)
                else -> GifPicker(onPick = { id, w, h -> RichBody.encodeGif(id, w, h)?.let(onRich) })
            }
        }
    }
}
