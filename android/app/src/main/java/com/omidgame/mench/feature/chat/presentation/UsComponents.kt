package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.core.ui.theme.MenchCalendar
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import com.omidgame.mench.core.ui.components.MenchDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omidgame.mench.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.random.Random

/** Whole days from [from] to today (negative if [from] is in the future). */
private fun daysSince(isoDate: String): Long? =
    runCatching { ChronoUnit.DAYS.between(LocalDate.parse(isoDate), LocalDate.now()) }.getOrNull()

private fun daysUntil(isoDate: String): Long? = daysSince(isoDate)?.let { -it }

/** ISO yyyy-MM-dd -> Jalali "yyyy/MM/dd" with Persian digits (ICU). */
private fun jalali(isoDate: String): String? = runCatching {
    val millis = LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    SimpleDateFormat("yyyy/MM/dd", MenchCalendar.locale()).format(Date(millis))
}.getOrNull()

private fun pickerMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

/**
 * The "ما" (us) card: days together, countdown to the next meeting, date
 * setters, and the big "I miss you" button. Both people see and can edit
 * the same dates (synced via the us.updated realtime event).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsDialog(
    togetherSince: String?,
    nextMeetDate: String?,
    onDismiss: () -> Unit,
    onMissYou: () -> Unit,
    onSetUsDates: (togetherSince: String?, nextMeetDate: String?) -> Unit,
    onOpenLetters: () -> Unit = {},
    onOpenMemories: () -> Unit = {},
    onOpenCounters: () -> Unit = {},
    onOpenCanvas: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenYear: () -> Unit = {},
    /** Phase 3A: sends a ready-made message (good-morning / good-night) to the chat. */
    onQuickMessage: (String) -> Unit = {},
    onOpenCards: () -> Unit = {},
    onOpenGames: () -> Unit = {},
    onOpenAlbum: () -> Unit = {},
    onOpenQuestion: () -> Unit = {},
    onOpenWishlist: () -> Unit = {},
    onSendAffection: (kind: String) -> Unit = {},
    moods: com.omidgame.mench.feature.chat.domain.Moods? = null,
    onSetMood: (emoji: String, text: String?) -> Unit = { _, _ -> },
    onSendHeartbeat: (durationMs: Long, onResult: (delivered: Boolean) -> Unit) -> Unit = { _, _ -> },
) {
    // Settings Phase 2: couples can hide the features they do not use.
    val usSettings = rememberUsSettings()
    var pickingTogether by remember { mutableStateOf(false) }
    var pickingMeet by remember { mutableStateOf(false) }

    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        title = {
            Text(
                text = "💗  " + stringResource(R.string.us_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val together = togetherSince?.let { daysSince(it) }
                if (together != null && together >= 0) {
                    // Hero card with the day counter; milestone days (every 100th, and anniversaries) throw confetti.
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                            .background(com.omidgame.mench.core.ui.theme.MenchBrand.heroBrush()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.us_days_together, together),
                            style = MaterialTheme.typography.headlineSmall,
                            color = androidx.compose.ui.graphics.Color.White,
                            textAlign = TextAlign.Center,
                        )
                        if (together > 0 && (together % 100L == 0L || together % 365L == 0L)) {
                            com.omidgame.mench.core.ui.components.ConfettiBurst(modifier = Modifier.matchParentSize())
                        }
                    }
                    jalali(togetherSince)?.let {
                        Text(stringResource(R.string.us_since, it), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(stringResource(R.string.us_set_together_hint), textAlign = TextAlign.Center)
                }

                val untilMeet = nextMeetDate?.let { daysUntil(it) }
                when {
                    untilMeet == null || untilMeet < 0 ->
                        Text(stringResource(R.string.us_set_meet_hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    untilMeet == 0L ->
                        Text(stringResource(R.string.us_meet_today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    else ->
                        Text(stringResource(R.string.us_days_until_meet, untilMeet), style = MaterialTheme.typography.titleMedium)
                }

                Button(onClick = onMissYou, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.us_miss_you))
                }
                HeartbeatHoldButton(onSend = onSendHeartbeat)
                if (usSettings.moodEnabled) MoodSection(moods = moods, onSetMood = onSetMood)

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { onSendAffection("hug") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.us_hug))
                    }
                    Button(onClick = { onSendAffection("kiss") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.us_kiss))
                    }
                }
                if (usSettings.questionEnabled) OutlinedButton(onClick = onOpenQuestion, modifier = Modifier.fillMaxWidth()) {
                    Text("❓  " + stringResource(R.string.question_title))
                }
                if (usSettings.wishlistEnabled) OutlinedButton(onClick = onOpenWishlist, modifier = Modifier.fillMaxWidth()) {
                    Text("✨  " + stringResource(R.string.wishlist_title))
                }
                val goodMorning = stringResource(R.string.ritual_morning_text)
                val goodNight = stringResource(R.string.ritual_night_text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onQuickMessage(goodMorning) }, modifier = Modifier.weight(1f)) {
                        Text("☀️ " + stringResource(R.string.ritual_morning))
                    }
                    OutlinedButton(onClick = { onQuickMessage(goodNight) }, modifier = Modifier.weight(1f)) {
                        Text("🌙 " + stringResource(R.string.ritual_night))
                    }
                }
                var comingOpen by remember { mutableStateOf(false) }
                if (comingOpen) {
                    val template = stringResource(R.string.coming_mood_text)
                    ImComingDialog(
                        onDismiss = { comingOpen = false },
                        onPick = { minutes ->
                            onSetMood("🚗", imComingText(template, minutes))
                            comingOpen = false
                        },
                    )
                }
                OutlinedButton(onClick = { comingOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("🚗  " + stringResource(R.string.coming_title))
                }
                OutlinedButton(onClick = onOpenYear, modifier = Modifier.fillMaxWidth()) {
                    Text("✨  " + stringResource(R.string.year_menu))
                }
                OutlinedButton(onClick = onOpenAlbum, modifier = Modifier.fillMaxWidth()) {
                    Text("🖼️  " + stringResource(R.string.album_title))
                }
                OutlinedButton(onClick = onOpenGames, modifier = Modifier.fillMaxWidth()) {
                    Text("🎮  " + stringResource(R.string.games_title))
                }
                OutlinedButton(onClick = onOpenCards, modifier = Modifier.fillMaxWidth()) {
                    Text("🎴  " + stringResource(R.string.cards_title))
                }
                androidx.compose.material3.Button(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
                    Text("💞  " + stringResource(R.string.profile_title))
                }
                com.omidgame.mench.core.ui.components.FeatureButton(
                    icon = com.omidgame.mench.core.ui.components.MenchIcons.Vault,
                    label = stringResource(R.string.vault_title),
                    onClick = onOpenVault,
                )
                com.omidgame.mench.core.ui.components.FeatureButton(
                    icon = com.omidgame.mench.core.ui.components.MenchIcons.Story,
                    label = stringResource(R.string.stories_title),
                    onClick = onOpenStories,
                )
                com.omidgame.mench.core.ui.components.FeatureButton(
                    icon = com.omidgame.mench.core.ui.components.MenchIcons.Canvas,
                    label = stringResource(R.string.canvas_title),
                    onClick = onOpenCanvas,
                )
                com.omidgame.mench.core.ui.components.FeatureButton(
                    icon = com.omidgame.mench.core.ui.components.MenchIcons.Counter,
                    label = stringResource(R.string.counters_title),
                    onClick = onOpenCounters,
                )
                if (usSettings.memoriesEnabled) OutlinedButton(onClick = onOpenMemories, modifier = Modifier.fillMaxWidth()) {
                    Text("📸  " + stringResource(R.string.memories_title))
                }
                OutlinedButton(onClick = onOpenLetters, modifier = Modifier.fillMaxWidth()) {
                    Text("💌  " + stringResource(R.string.letters_title))
                }
                OutlinedButton(onClick = { pickingTogether = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.us_set_together))
                }
                OutlinedButton(onClick = { pickingMeet = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.us_set_meet))
                }
            }
        },
    )

    if (pickingTogether || pickingMeet) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingTogether = false; pickingMeet = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val iso = pickerMillisToIso(millis)
                            if (pickingTogether) onSetUsDates(iso, null) else onSetUsDates(null, iso)
                        }
                        pickingTogether = false
                        pickingMeet = false
                    },
                ) { Text(stringResource(R.string.us_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTogether = false; pickingMeet = false }) {
                    Text(stringResource(R.string.us_cancel))
                }
            },
        ) { DatePicker(state = state) }
    }
}

private data class FloatingHeart(val xFraction: Float, val delayFraction: Float, val sizeSp: Int, val glyph: String)

/**
 * A burst of hearts floating up the screen. Each increase of [burstKey]
 * (> 0) plays one burst; touches pass straight through since the overlay
 * has no click handlers.
 */
@Composable
fun HeartsOverlay(
    burstKey: Int,
    modifier: Modifier = Modifier,
    glyphs: List<String> = listOf("💗", "💖", "💕", "❤️", "💓"),
) {
    if (burstKey <= 0) return
    val progress = remember(burstKey) { Animatable(0f) }
    val hearts = remember(burstKey) {
        List(16) {
            FloatingHeart(
                xFraction = Random.nextFloat(),
                delayFraction = Random.nextFloat() * 0.4f,
                sizeSp = Random.nextInt(22, 44),
                glyph = glyphs.random(),
            )
        }
    }
    LaunchedEffect(burstKey) { progress.animateTo(1f, tween(2600, easing = LinearEasing)) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val widthDp = maxWidth
        hearts.forEach { heart ->
            val local = ((progress.value - heart.delayFraction) / (1f - heart.delayFraction)).coerceIn(0f, 1f)
            if (local > 0f && local < 1f) {
                Box(
                    modifier = Modifier
                        .offset(x = widthDp * heart.xFraction * 0.9f)
                        .graphicsLayer {
                            translationY = heightPx * (1f - local) - 40f
                            alpha = if (local > 0.75f) (1f - local) * 4f else 1f
                        },
                ) { Text(heart.glyph, fontSize = heart.sizeSp.sp) }
            }
        }
    }
}

/** Hug = slow, soft, long squeezes. Kiss = one tiny quick pop. */
fun vibrateAffection(context: Context, kind: String) {
    if (!com.omidgame.mench.core.ui.theme.HapticSettings.get(context, com.omidgame.mench.core.ui.theme.HapticSettings.KEY_AFFECTION)) return
    val pattern = if (kind == "hug") longArrayOf(0, 350, 120, 350, 120, 500) else longArrayOf(0, 30, 70, 45)
    runCatching {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.let { it.vibrate(UsSettingsStore.effectFor(context, it, pattern)) }
    }
}

/** Glyphs floated by [HeartsOverlay] for each gesture. */
fun glyphsFor(kind: String): List<String> = when (kind) {
    "hug" -> listOf("🤗", "🫂", "💞", "🧡", "💛")
    "kiss" -> listOf("😘", "💋", "😚", "💗", "❤️")
    else -> listOf("💗", "💖", "💕", "❤️", "💓")
}

/** Two quick "lub-dub" beats, repeated — a heartbeat. Silently does nothing if the device has no vibrator. */
fun vibrateHeartbeat(context: Context) {
    if (!com.omidgame.mench.core.ui.theme.HapticSettings.get(context, com.omidgame.mench.core.ui.theme.HapticSettings.KEY_AFFECTION)) return
    runCatching {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.let { it.vibrate(UsSettingsStore.effectFor(context, it, longArrayOf(0, 70, 110, 70, 500, 70, 110, 70))) }
    }
}

private fun formatJalaliMillis(millis: Long): String =
    SimpleDateFormat("yyyy/MM/dd", MenchCalendar.locale()).format(Date(millis))

/**
 * The "open when…" letters list. A sealed letter shows only its title (the
 * body never reaches the recipient's phone until they open it); the author
 * sees everything they wrote. Tapping a ready letter asks the server to
 * open it, then shows the body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LettersDialog(
    letters: List<com.omidgame.mench.feature.chat.domain.Letter>,
    onDismiss: () -> Unit,
    onOpenLetter: (letterId: String) -> Unit,
    onCreateLetter: (title: String, body: String, unlockAtIso: String?, photo: com.omidgame.mench.feature.chat.domain.PickedPhoto?) -> Unit,
) {
    var composing by remember { mutableStateOf(false) }
    var readingId by remember { mutableStateOf<String?>(null) }
    // The letter that was sealed a moment ago: its reading view plays the envelope-opening animation.
    var revealId by remember { mutableStateOf<String?>(null) }
    val reading = letters.firstOrNull { it.id == readingId }

    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        dismissButton = { TextButton(onClick = { composing = true }) { Text(stringResource(R.string.letters_write)) } },
        title = { Text("💌  " + stringResource(R.string.letters_title)) },
        text = {
            if (letters.isEmpty()) {
                Text(stringResource(R.string.letters_empty))
            } else {
                androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(letters.size) { index ->
                        val letter = letters[index]
                        val sealed = !letter.isMine && letter.body == null
                        val waiting = sealed && !letter.canOpen
                        androidx.compose.material3.Card(
                            onClick = {
                                when {
                                    waiting -> Unit
                                    sealed -> { onOpenLetter(letter.id); readingId = letter.id; revealId = letter.id }
                                    else -> readingId = letter.id
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            if (sealed) {
                                WaxSeal(size = 36.dp)
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(end = 10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = (if (sealed) "" else if (letter.isMine) "✍️ " else "💗 ") + letter.title,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val sub = when {
                                    letter.isMine && letter.openedAtEpochMillis != null -> stringResource(R.string.letters_status_read)
                                    letter.isMine -> stringResource(R.string.letters_status_sent)
                                    waiting -> stringResource(
                                        R.string.letters_opens_on,
                                        formatJalaliMillis(letter.unlockAtEpochMillis ?: 0L),
                                    )
                                    sealed -> stringResource(R.string.letters_tap_to_open)
                                    else -> null
                                }
                                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
                                if (waiting && letter.unlockAtEpochMillis != null) LetterCountdownText(letter.unlockAtEpochMillis)
                            }
                            }
                        }
                    }
                }
            }
        },
    )

    if (reading != null && reading.body != null) {
        MenchDialog(
            onDismissRequest = { readingId = null },
            confirmButton = { TextButton(onClick = { readingId = null }) { Text(stringResource(R.string.us_close)) } },
            title = { Text("💗 " + reading.title) },
            text = {
                LetterReveal(title = reading.title, body = reading.body, animate = reading.id == revealId, attachmentId = reading.attachmentId)
            },
        )
    }

    if (composing) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var unlockIso by remember { mutableStateOf<String?>(null) }
        var pickingDate by remember { mutableStateOf(false) }
        var letterPhoto by remember { mutableStateOf<com.omidgame.mench.feature.chat.domain.PickedPhoto?>(null) }
        val pickLetterPhoto = rememberPhotoPicker(androidx.compose.ui.platform.LocalContext.current) { letterPhoto = it }
        MenchDialog(
            onDismissRequest = { composing = false },
            title = { Text(stringResource(R.string.letters_write)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 80) title = it },
                        label = { Text(stringResource(R.string.letters_title_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = pickLetterPhoto) {
                        Text(if (letterPhoto == null) "📎 " + stringResource(R.string.letters_attach_photo) else "✅ " + stringResource(R.string.letters_photo_attached))
                    }
                    val openWhen = listOf(
                        stringResource(R.string.letters_open_sad),
                        stringResource(R.string.letters_open_miss),
                        stringResource(R.string.letters_open_sleep),
                        stringResource(R.string.letters_open_happy),
                    )
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(openWhen.size) { i ->
                            androidx.compose.material3.AssistChip(onClick = { title = openWhen[i] }, label = { Text(openWhen[i]) })
                        }
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = body,
                        onValueChange = { if (it.length <= 5000) body = it },
                        label = { Text(stringResource(R.string.letters_body_hint)) },
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                            fontFamily = com.omidgame.mench.core.ui.theme.rememberGulzarFamily(),
                            fontSize = 18.sp,
                            lineHeight = 34.sp,
                        ),
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            unlockIso?.let { iso -> stringResource(R.string.letters_unlock_on, jalali(iso) ?: iso) }
                                ?: stringResource(R.string.letters_unlock_optional),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank() && body.isNotBlank(),
                    onClick = {
                        // Unlock at the start of the chosen day in the device's timezone.
                        val unlockInstant = unlockIso?.let {
                            LocalDate.parse(it).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString()
                        }
                        onCreateLetter(title.trim(), body.trim(), unlockInstant, letterPhoto)
                        composing = false
                    },
                ) { Text(stringResource(R.string.letters_send)) }
            },
            dismissButton = { TextButton(onClick = { composing = false }) { Text(stringResource(R.string.us_cancel)) } },
        )
        if (pickingDate) {
            val state = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { unlockIso = pickerMillisToIso(it) }
                        pickingDate = false
                    }) { Text(stringResource(R.string.us_confirm)) }
                },
                dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.us_cancel)) } },
            ) { DatePicker(state = state) }
        }
    }
}

/**
 * The shared memories timeline: newest memory-date first, each with its
 * Jalali date, title, optional photo (loaded through the app's authenticated
 * Coil ImageLoader, same as chat images) and note. Either person can add;
 * only the author can delete their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesDialog(
    memories: List<com.omidgame.mench.feature.chat.domain.Memory>,
    onDismiss: () -> Unit,
    onDelete: (memoryId: String) -> Unit,
    onCreate: (title: String, note: String?, dateIso: String, photoUri: String?, photoMime: String?, photoName: String?) -> Unit,
) {
    var composing by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var yearFilter by remember { mutableStateOf<Int?>(null) }

    MenchDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
        dismissButton = { TextButton(onClick = { composing = true }) { Text(stringResource(R.string.memories_add)) } },
        title = { Text("📸  " + stringResource(R.string.memories_title)) },
        text = {
            val shown = memoriesOfYear(memories, yearFilter)
            if (memories.isEmpty()) {
                Text(stringResource(R.string.memories_empty))
            } else {
                androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val years = memoryYears(memories)
                    if (years.size > 1) {
                        item {
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    androidx.compose.material3.FilterChip(
                                        selected = yearFilter == null,
                                        onClick = { yearFilter = null },
                                        label = { Text(stringResource(R.string.memories_all_years)) },
                                    )
                                }
                                items(years.size) { i ->
                                    androidx.compose.material3.FilterChip(
                                        selected = yearFilter == years[i],
                                        onClick = { yearFilter = years[i] },
                                        label = { Text(years[i].toString()) },
                                    )
                                }
                            }
                        }
                    }
                    memoryOfTheWeek(memories, java.time.LocalDate.now())?.let { weekly ->
                        item {
                            androidx.compose.material3.Card(
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                                modifier = Modifier.fillMaxWidth().tiltOnTouch(4f),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("⭐ " + stringResource(R.string.memory_of_week), style = MaterialTheme.typography.titleSmall)
                                    Text(weekly.title, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    val todays = onThisDay(memories, java.time.LocalDate.now())
                    if (todays.isNotEmpty()) {
                        item {
                            androidx.compose.material3.Card(
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("📅 " + stringResource(R.string.on_this_day), style = MaterialTheme.typography.titleSmall)
                                    todays.forEach {
                                        Text(
                                            stringResource(R.string.on_this_day_item, it.yearsAgo, it.memory.title),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(shown.size) { index ->
                        val memory = shown[index]
                        androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = jalali(memory.memoryDate) ?: memory.memoryDate,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text("💗 " + memory.title, style = MaterialTheme.typography.titleSmall)
                                memory.attachmentId?.let { id ->
                                    coil.compose.AsyncImage(
                                        model = com.omidgame.mench.core.network.AttachmentUrls.thumbnail(id),
                                        contentDescription = memory.title,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                memory.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                if (memory.isMine) {
                                    TextButton(onClick = { confirmDeleteId = memory.id }) {
                                        Text(stringResource(R.string.memories_delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )

    confirmDeleteId?.let { id ->
        MenchDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text(stringResource(R.string.memories_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { onDelete(id); confirmDeleteId = null }) { Text(stringResource(R.string.memories_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text(stringResource(R.string.us_cancel)) } },
        )
    }

    if (composing) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var title by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var dateIso by remember { mutableStateOf(LocalDate.now().toString()) }
        var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
        var pickingDate by remember { mutableStateOf(false) }
        val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        ) { uri -> if (uri != null) photoUri = uri }

        MenchDialog(
            onDismissRequest = { composing = false },
            title = { Text(stringResource(R.string.memories_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 80) title = it },
                        label = { Text(stringResource(R.string.memories_title_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= 2000) note = it },
                        label = { Text(stringResource(R.string.memories_note_hint)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.memories_date, jalali(dateIso) ?: dateIso))
                    }
                    OutlinedButton(onClick = { pickPhoto.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                if (photoUri == null) R.string.memories_add_photo else R.string.memories_photo_selected,
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        val uri = photoUri
                        val mime = uri?.let { context.contentResolver.getType(it) }
                        val name = uri?.lastPathSegment?.substringAfterLast('/')
                        onCreate(title.trim(), note.trim().ifBlank { null }, dateIso, uri?.toString(), mime, name)
                        composing = false
                    },
                ) { Text(stringResource(R.string.letters_send)) }
            },
            dismissButton = { TextButton(onClick = { composing = false }) { Text(stringResource(R.string.us_cancel)) } },
        )
        if (pickingDate) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = LocalDate.parse(dateIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { dateIso = pickerMillisToIso(it) }
                        pickingDate = false
                    }) { Text(stringResource(R.string.us_confirm)) }
                },
                dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.us_cancel)) } },
            ) { DatePicker(state = state) }
        }
    }
}

/** The mood picked "today" only counts for the local day it was set; after midnight it quietly disappears. */
fun com.omidgame.mench.feature.chat.domain.Mood?.todayOrNull(): com.omidgame.mench.feature.chat.domain.Mood? {
    if (this == null) return null
    val day = Instant.ofEpochMilli(setAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return if (day == LocalDate.now()) this else null
}

private val MOOD_CHOICES = listOf("🥰", "😍", "😊", "😎", "😴", "🥱", "😢", "😡", "🤒", "💪")

/** "حال امروز": my mood (tap an emoji, optional short note) and the partner's, side by side. */
@Composable
private fun MoodSection(
    moods: com.omidgame.mench.feature.chat.domain.Moods?,
    onSetMood: (emoji: String, text: String?) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    val mine = moods?.mine.todayOrNull()
    val other = moods?.other.todayOrNull()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.mood_title), style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(MOOD_CHOICES.size) { i ->
                val emoji = MOOD_CHOICES[i]
                TextButton(onClick = { onSetMood(emoji, note.trim().ifBlank { null }) }) {
                    Text(emoji, fontSize = if (mine?.emoji == emoji) 30.sp else 22.sp)
                }
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 60) note = it },
            placeholder = { Text(stringResource(R.string.mood_note_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.mood_mine, mine?.let { it.emoji + (it.text?.let { t -> " $t" } ?: "") } ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.mood_other, other?.let { it.emoji + (it.text?.let { t -> " $t" } ?: "") } ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (mine != null && other != null) MoodDuetBanner(mine.emoji, other.emoji)
    }
}
