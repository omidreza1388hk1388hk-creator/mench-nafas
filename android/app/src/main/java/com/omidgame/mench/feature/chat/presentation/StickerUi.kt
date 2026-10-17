package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog

/** True when the UI language is Persian — decides which caption of a sticker is shown. */
@Composable
fun isPersianUi(): Boolean = LocalConfiguration.current.locales[0].language == "fa"

/** One sticker, drawn from its [StickerSpec]: gradient card, big emoji, caption. Scales with [size]. */
@Composable
fun StickerView(spec: StickerSpec, size: Dp, modifier: Modifier = Modifier) {
    val (top, bottom) = StickerCatalog.palettes.getOrElse(spec.palette) { StickerCatalog.palettes[0] }
    val caption = spec.caption(isPersianUi())
    val description = if (caption.isNotBlank()) caption else spec.emoji
    val hasCaption = caption.isNotBlank()
    Column(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .background(Brush.verticalGradient(listOf(Color(top), Color(bottom))))
            .border(BorderStroke(size * 0.02f, Color.White.copy(alpha = 0.55f)), RoundedCornerShape(size * 0.2f))
            .padding(horizontal = size * 0.06f, vertical = size * 0.05f)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = spec.emoji,
            fontSize = (size.value * if (hasCaption) 0.44f else 0.6f).sp,
            lineHeight = (size.value * if (hasCaption) 0.52f else 0.7f).sp,
            textAlign = TextAlign.Center,
        )
        if (hasCaption) {
            Text(
                text = caption,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.105f).coerceAtLeast(9f).sp,
                lineHeight = (size.value * 0.13f).coerceAtLeast(11f).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = size * 0.03f),
            )
        }
    }
}

private fun categoryLabel(category: StickerCategory): Int = when (category) {
    StickerCategory.LOVE -> R.string.sticker_cat_love
    StickerCategory.MISS -> R.string.sticker_cat_miss
    StickerCategory.AFFECTION -> R.string.sticker_cat_affection
    StickerCategory.GREETING -> R.string.sticker_cat_greeting
    StickerCategory.CUTE -> R.string.sticker_cat_cute
    StickerCategory.CELEBRATE -> R.string.sticker_cat_celebrate
}

/**
 * The sticker tab of the media panel: "make your own" cell, recents, then every category.
 * [onPick] receives the ready-to-send message body (see [RichBody]).
 */
@Composable
fun StickerTray(onPick: (body: String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val recents = remember { mutableStateListOf<String>().apply { addAll(ChatPrefs.recentStickers(context)) } }
    var showMaker by remember { mutableStateOf(false) }

    fun pick(body: String) {
        ChatPrefs.pushRecentSticker(context, body)
        recents.remove(body)
        recents.add(0, body)
        onPick(body)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "maker", span = { GridItemSpan(1) }) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { showMaker = true },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.sticker_make_own),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        if (recents.isNotEmpty()) {
            item(key = "h-recent", span = { GridItemSpan(maxLineSpan) }) { TrayHeader(stringResource(R.string.sticker_recent)) }
            items(recents.toList(), key = { "r-$it" }) { body ->
                val sticker = (RichBody.parse(body) as? RichBody.Sticker)?.spec
                if (sticker != null) {
                    Box(modifier = Modifier.aspectRatio(1f).clickable { pick(body) }) { StickerView(sticker, TRAY_STICKER_SIZE, Modifier.fillMaxSize()) }
                }
            }
        }
        for (category in StickerCategory.values()) {
            item(key = "h-" + category.name, span = { GridItemSpan(maxLineSpan) }) { TrayHeader(stringResource(categoryLabel(category))) }
            items(StickerCatalog.inCategory(category), key = { it.id }) { sticker ->
                Box(modifier = Modifier.aspectRatio(1f).clickable { pick(RichBody.encodeSticker(sticker)) }) {
                    StickerView(StickerCatalog.spec(sticker), TRAY_STICKER_SIZE, Modifier.fillMaxSize())
                }
            }
        }
    }

    if (showMaker) {
        CustomStickerDialog(
            onSend = { body ->
                showMaker = false
                pick(body)
            },
            onDismiss = { showMaker = false },
        )
    }
}

private val TRAY_STICKER_SIZE = 104.dp

@Composable
private fun TrayHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
    )
}

/** "Make your own": pick emoji + write a caption + pick a colour, with a live preview. */
@Composable
fun CustomStickerDialog(onSend: (body: String) -> Unit, onDismiss: () -> Unit) {
    var emoji by remember { mutableStateOf("\uD83D\uDC97") }
    var caption by remember { mutableStateOf("") }
    var palette by remember { mutableIntStateOf(0) }
    val body = RichBody.encodeCustomSticker(emoji, caption, palette)
    val spec = (RichBody.parse(body) as? RichBody.Sticker)?.spec
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sticker_make_own)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (spec != null) StickerView(spec, 120.dp)
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(24) },
                    singleLine = true,
                    isError = body == null,
                    label = { Text(stringResource(R.string.sticker_emoji_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(StickerCatalog.MAX_CUSTOM_CAPTION) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sticker_caption_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StickerCatalog.palettes.forEachIndexed { index, (top, bottom) ->
                        Box(
                            modifier = Modifier
                                .size(if (index == palette) 30.dp else 26.dp)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(Color(top), Color(bottom))))
                                .then(
                                    if (index == palette) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                                )
                                .clickable { palette = index },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { body?.let(onSend) }, enabled = body != null) { Text(stringResource(R.string.chat_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
