package com.omidgame.mench.feature.chat.presentation

import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.togetherWith
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.feature.chat.domain.Counter
import com.omidgame.mench.feature.chat.domain.Letter
import com.omidgame.mench.feature.chat.domain.Memory
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** Phase 3A — "Year in Love" summary and the couple conversation cards. Both use data already on the phone; nothing new is stored. */

data class YearStats(
    val year: Int,
    /** Days since [togetherSince]; null if no date is set. */
    val daysTogether: Long?,
    val memoriesThisYear: Int,
    /** Title of the earliest memory dated this year, if any. */
    val firstMemoryTitle: String?,
    val lettersWritten: Int,
    val lettersReceived: Int,
    val counterCount: Int,
    /** Sum of all tally counters. */
    val tallyTotal: Int,
)

internal fun computeYearStats(
    today: LocalDate,
    togetherSinceIso: String?,
    memories: List<Memory>,
    letters: List<Letter>,
    counters: List<Counter>,
): YearStats {
    val thisYear = memories
        .mapNotNull { m -> runCatching { LocalDate.parse(m.memoryDate) }.getOrNull()?.let { it to m } }
        .filter { it.first.year == today.year }
        .sortedBy { it.first }
    val zone = java.time.ZoneId.systemDefault()
    val yearLetters = letters.filter {
        java.time.Instant.ofEpochMilli(it.createdAtEpochMillis).atZone(zone).year == today.year
    }
    return YearStats(
        year = today.year,
        daysTogether = togetherSinceIso?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), today) }.getOrNull() },
        memoriesThisYear = thisYear.size,
        firstMemoryTitle = thisYear.firstOrNull()?.second?.title,
        lettersWritten = yearLetters.count { it.isMine },
        lettersReceived = yearLetters.count { !it.isMine },
        counterCount = counters.size,
        tallyTotal = counters.filter { it.kind == "tally" }.sumOf { it.tallyCount },
    )
}

/** The lines of the plain-text summary shared from the last page; each is already formatted for the user's language. */
internal fun yearShareText(lines: List<String>): String = lines.filter { it.isNotBlank() }.joinToString("\n")

/**
 * Phase 3A — "Year in Love" as a story: full-screen pages (tap the end side to go on, the start side to go back) built from
 * what you two saved and from the statistics of your own chat, ending with a share button. Everything is computed on the phone.
 */
@Composable
fun YearInLoveDialog(stats: YearStats, messages: com.omidgame.mench.feature.chat.domain.YearMessageStats?, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val hasMessages = messages != null && messages.total > 0

    // One entry per page; the lambda draws the page's content. Pages with nothing to say are simply left out.
    val pages = mutableListOf<@Composable () -> Unit>()
    pages += {
        PageText("✨", stringResource(R.string.year_title, stats.year))
        stats.daysTogether?.takeIf { it >= 0 }?.let {
            RollingNumber(it, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.profile_days_together), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
    pages += {
        PageText("📸", stringResource(R.string.year_memories, stats.memoriesThisYear))
        stats.firstMemoryTitle?.let { PageText("🌱", stringResource(R.string.year_first_memory, it)) }
    }
    if (hasMessages && messages != null) {
        pages += {
            Text("💬", fontSize = 56.sp)
            RollingNumber(messages.sent.toLong(), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.year_msgs_sent), color = Color.White, style = MaterialTheme.typography.titleMedium)
            RollingNumber(messages.received.toLong(), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.year_msgs_received), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        messages.busiestHour?.let { hour ->
            pages += {
                PageText("🌙", stringResource(R.string.year_busiest_hour, "%02d:00".format(hour)))
                messages.busiestDay?.let { day ->
                    PageText("📅", stringResource(R.string.year_busiest_day, day.toString(), messages.busiestDayCount))
                }
            }
        }
        messages.topEmoji?.let { e ->
            pages += {
                Text(e, fontSize = 96.sp)
                PageText("", stringResource(R.string.year_top_emoji, messages.topEmojiCount))
            }
        }
        if (messages.topWords.isNotEmpty()) {
            pages += {
                PageText("🗝️", stringResource(R.string.year_top_words))
                messages.topWords.forEach { Text(it, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    pages += {
        PageText("💌", stringResource(R.string.year_letters, stats.lettersWritten, stats.lettersReceived))
        if (stats.counterCount > 0) PageText("🔢", stringResource(R.string.year_counters, stats.counterCount, stats.tallyTotal))
    }
    val shareLines = listOf(
        stringResource(R.string.year_title, stats.year),
        stats.daysTogether?.takeIf { it >= 0 }?.let { stringResource(R.string.year_days_together, it) }.orEmpty(),
        stringResource(R.string.year_memories, stats.memoriesThisYear),
        if (hasMessages && messages != null) stringResource(R.string.year_share_msgs, messages.total) else "",
        stringResource(R.string.year_footer),
    )
    val shareChooserTitle = stringResource(R.string.year_share)
    val last = pages.size // the extra final page: the share button

    var index by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, last)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(storyBrush(safeIndex))
                .pointerInput(safeIndex, rtl) {
                    detectTapGestures { offset ->
                        val tappedEnd = if (rtl) offset.x < size.width / 2f else offset.x >= size.width / 2f
                        val next = stepStory(safeIndex, if (tappedEnd) 1 else -1, last + 1)
                        if (next == null) { if (tappedEnd) onDismiss() } else index = next
                    }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    for (i in 0..last) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(if (i <= safeIndex) Color.White else Color.White.copy(alpha = 0.3f)),
                        )
                    }
                }
                androidx.compose.animation.AnimatedContent(
                    targetState = safeIndex,
                    transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "yearPage",
                ) { page ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    ) {
                        if (page < last) {
                            pages[page]()
                        } else {
                            SparkleBurst(size = 96.dp, color = Color.White)
                            PageText("💗", stringResource(R.string.year_footer))
                            Button(onClick = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, yearShareText(shareLines))
                                }
                                context.startActivity(android.content.Intent.createChooser(send, shareChooserTitle))
                            }) { Text(stringResource(R.string.year_share)) }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.us_close), color = Color.White)
                }
            }
        }
    }
}

/** A big emoji above a centred white line of text; one page row. */
@Composable
private fun PageText(emoji: String, text: String) {
    if (emoji.isNotBlank()) Text(emoji, fontSize = 56.sp)
    Text(text, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
}

/** Index of a random card in a deck of [size] that differs from [previous] when possible. */
internal fun nextCardIndex(size: Int, previous: Int, random: Random = Random.Default): Int {
    if (size <= 1) return 0
    var next = random.nextInt(size)
    if (next == previous) next = (next + 1) % size
    return next
}

private enum class CardDeck { DEEP, FUN, DARE }

/** Three decks of conversation cards: deep questions, fun ones, and small dares. Pure inspiration, nothing is sent or saved. */
@Composable
fun CoupleCardsDialog(onDismiss: () -> Unit) {
    var deck by remember { mutableStateOf(CardDeck.DEEP) }
    val cards = when (deck) {
        CardDeck.DEEP -> stringArrayResource(R.array.couple_cards_deep)
        CardDeck.FUN -> stringArrayResource(R.array.couple_cards_fun)
        CardDeck.DARE -> stringArrayResource(R.array.couple_cards_dare)
    }
    var index by remember(deck) { mutableStateOf(nextCardIndex(cards.size, -1)) }
    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        title = { Text("🎴  " + stringResource(R.string.cards_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = deck == CardDeck.DEEP, onClick = { deck = CardDeck.DEEP }, label = { Text(stringResource(R.string.cards_deck_deep)) })
                    FilterChip(selected = deck == CardDeck.FUN, onClick = { deck = CardDeck.FUN }, label = { Text(stringResource(R.string.cards_deck_fun)) })
                    FilterChip(selected = deck == CardDeck.DARE, onClick = { deck = CardDeck.DARE }, label = { Text(stringResource(R.string.cards_deck_dare)) })
                }
                Card(modifier = Modifier.fillMaxWidth().tiltOnTouch()) {
                    Text(
                        text = cards.getOrElse(index) { "" },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    )
                }
                Button(onClick = { index = nextCardIndex(cards.size, index) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cards_next))
                }
            }
        },
    )
}
