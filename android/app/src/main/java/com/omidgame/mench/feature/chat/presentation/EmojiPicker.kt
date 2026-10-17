package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omidgame.mench.R

private val SKIN_TONE_SWATCH = listOf(
    Color(0xFFFFC83D), Color(0xFFFADCBC), Color(0xFFE0BB95), Color(0xFFBF8F68), Color(0xFF9B643D), Color(0xFF594539),
)

/**
 * Phase 2A emoji picker: search (English + Persian keywords), recents, category tabs and a
 * skin-tone selector for hand/person emoji. Replaces the flat EmojiPanel grid in the composer.
 * Data and search live in [EmojiData] (pure Kotlin, unit-tested); preferences in [ChatPrefs].
 */
@Composable
fun EmojiPicker(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var tone by remember { mutableIntStateOf(ChatPrefs.skinTone(context)) }
    var category by remember { mutableStateOf(EmojiCategory.LOVE) }
    val recents = remember { mutableStateListOf<String>().apply { addAll(ChatPrefs.recentEmoji(context)) } }

    fun pick(emoji: String) {
        ChatPrefs.pushRecentEmoji(context, emoji)
        recents.remove(emoji)
        recents.add(0, emoji)
        onPick(emoji)
    }

    val searching = query.isNotBlank()
    val entries: List<EmojiEntry> = remember(query, category) {
        when {
            query.isNotBlank() -> EmojiData.search(query)
            category == EmojiCategory.RECENT -> emptyList()
            else -> EmojiData.categories[category].orEmpty()
        }
    }

    Column(modifier = modifier.fillMaxWidth().height(300.dp)) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.emoji_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        if (!searching) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    items(EmojiCategory.values().toList(), key = { it.name }) { cat ->
                        val selected = cat == category
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { category = cat }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(categoryGlyph(cat), fontSize = 20.sp) }
                    }
                }
                // Tapping the swatch cycles the default skin tone for hand / person emoji.
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(SKIN_TONE_SWATCH[tone])
                        .clickable {
                            tone = (tone + 1) % EmojiData.TONE_COUNT
                            ChatPrefs.setSkinTone(context, tone)
                        },
                )
            }
        }
        when {
            searching && entries.isEmpty() -> EmptyHint(stringResource(R.string.emoji_no_results))
            !searching && category == EmojiCategory.RECENT && recents.isEmpty() ->
                EmptyHint(stringResource(R.string.emoji_no_recent))
            !searching && category == EmojiCategory.RECENT -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(44.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp),
                ) {
                    items(recents.toList(), key = { it }) { emoji -> EmojiCell(emoji) { pick(emoji) } }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(44.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp),
                ) {
                    items(entries, key = { it.base }) { entry ->
                        val shown = EmojiData.withTone(entry, tone)
                        EmojiCell(shown) { pick(shown) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 26.sp) }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun categoryGlyph(category: EmojiCategory): String = when (category) {
    EmojiCategory.RECENT -> "\uD83D\uDD52"   // clock
    EmojiCategory.LOVE -> "\u2764\uFE0F"
    EmojiCategory.SMILEYS -> "\uD83D\uDE0A"
    EmojiCategory.GESTURES -> "\uD83D\uDC4D"
    EmojiCategory.NATURE -> "\uD83D\uDC31"
    EmojiCategory.FOOD -> "\uD83C\uDF55"
    EmojiCategory.TRAVEL -> "\u2708\uFE0F"
    EmojiCategory.OBJECTS -> "\uD83D\uDCA1"
    EmojiCategory.SYMBOLS -> "\uD83D\uDCAF"
}
