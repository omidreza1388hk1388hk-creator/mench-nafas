package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchDialog
import com.omidgame.mench.feature.chat.domain.QuizDetail
import com.omidgame.mench.feature.chat.domain.QuizQuestion
import com.omidgame.mench.feature.chat.domain.QuizResult
import com.omidgame.mench.feature.chat.domain.QuizSummary
import java.text.DateFormat
import java.util.Date

/** Phase 3A — couple games: 36 questions, would-you-rather, a love-language quiz, and "how well do you know me" quizzes. */

// ---------------- pure logic ----------------

enum class LoveLanguage(val nameRes: Int, val descRes: Int) {
    WORDS(R.string.lang_words, R.string.lang_words_desc),
    TIME(R.string.lang_time, R.string.lang_time_desc),
    GIFTS(R.string.lang_gifts, R.string.lang_gifts_desc),
    ACTS(R.string.lang_acts, R.string.lang_acts_desc),
    TOUCH(R.string.lang_touch, R.string.lang_touch_desc),
}

/**
 * The language each of the two options of every question stands for — in the order of `games_love_pairs`.
 * The ten pairs cover every combination of two of the five languages exactly once.
 */
internal val lovePairs: List<Pair<LoveLanguage, LoveLanguage>> = listOf(
    LoveLanguage.WORDS to LoveLanguage.TIME,
    LoveLanguage.GIFTS to LoveLanguage.ACTS,
    LoveLanguage.TOUCH to LoveLanguage.WORDS,
    LoveLanguage.TIME to LoveLanguage.GIFTS,
    LoveLanguage.TOUCH to LoveLanguage.ACTS,
    LoveLanguage.TIME to LoveLanguage.TOUCH,
    LoveLanguage.TIME to LoveLanguage.ACTS,
    LoveLanguage.GIFTS to LoveLanguage.TOUCH,
    LoveLanguage.WORDS to LoveLanguage.GIFTS,
    LoveLanguage.ACTS to LoveLanguage.WORDS,
)

/** [choseFirst] has one entry per answered question: true = the first option, false = the second. */
internal fun scoreLoveLanguages(choseFirst: List<Boolean>): Map<LoveLanguage, Int> {
    val scores = LoveLanguage.entries.associateWith { 0 }.toMutableMap()
    choseFirst.forEachIndexed { i, first ->
        val pair = lovePairs.getOrNull(i) ?: return@forEachIndexed
        val picked = if (first) pair.first else pair.second
        scores[picked] = (scores[picked] ?: 0) + 1
    }
    return scores
}

/** The language(s) with the highest score (more than one on a tie), in declaration order; empty if nothing was answered. */
internal fun topLoveLanguages(scores: Map<LoveLanguage, Int>): List<LoveLanguage> {
    val best = scores.values.maxOrNull() ?: return emptyList()
    if (best == 0) return emptyList()
    return LoveLanguage.entries.filter { scores[it] == best }
}

/** A question as typed on screen: three option boxes and the index of the one marked right. */
data class QuizDraft(val text: String = "", val options: List<String> = listOf("", "", ""), val correct: Int = 0)

/** The question to publish, or null while it is incomplete. Blank options are dropped and the right-answer index follows. */
internal fun buildQuestion(draft: QuizDraft): QuizQuestion? {
    if (draft.text.isBlank()) return null
    val correctText = draft.options.getOrNull(draft.correct)?.trim().orEmpty()
    if (correctText.isEmpty()) return null
    val kept = draft.options.mapIndexedNotNull { i, o -> o.trim().takeIf { it.isNotEmpty() }?.let { i to it } }
    if (kept.size < 2) return null
    val newCorrect = kept.indexOfFirst { it.first == draft.correct }
    return QuizQuestion(draft.text.trim(), kept.map { it.second }, newCorrect)
}

private const val HISTORY_PREFS = "love_language_history"
private const val HISTORY_KEY = "entries"

/** Past love-language results kept on this phone: (time, language), newest first, at most 8. */
internal fun loveHistory(context: Context): List<Pair<Long, LoveLanguage>> =
    (context.applicationContext.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).getString(HISTORY_KEY, "") ?: "")
        .lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            val time = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val lang = runCatching { LoveLanguage.valueOf(parts.getOrNull(1).orEmpty()) }.getOrNull() ?: return@mapNotNull null
            time to lang
        }.toList()

internal fun saveLoveResult(context: Context, language: LoveLanguage) {
    val updated = (listOf(System.currentTimeMillis() to language) + loveHistory(context)).take(8)
    context.applicationContext.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).edit()
        .putString(HISTORY_KEY, updated.joinToString("\n") { "${it.first}|${it.second.name}" }).apply()
}

// ---------------- UI ----------------

private enum class GameScreen { MENU, THIRTY_SIX, WYR, LOVE, QUIZZES }

@Composable
fun GamesHubDialog(
    quizzes: List<QuizSummary>,
    onDismiss: () -> Unit,
    onCreateQuiz: (String, List<QuizQuestion>) -> Unit,
    onLoadQuiz: (String, (QuizDetail?) -> Unit) -> Unit,
    onSubmitQuiz: (String, List<Int>, (QuizResult?) -> Unit) -> Unit,
    onDeleteQuiz: (String) -> Unit,
) {
    var screen by remember { mutableStateOf(GameScreen.MENU) }
    val back = { screen = GameScreen.MENU }
    when (screen) {
        GameScreen.MENU -> MenchDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_close)) } },
            title = { Text("🎮  " + stringResource(R.string.games_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { screen = GameScreen.THIRTY_SIX }, modifier = Modifier.fillMaxWidth()) { Text("💞  " + stringResource(R.string.games_36)) }
                    OutlinedButton(onClick = { screen = GameScreen.WYR }, modifier = Modifier.fillMaxWidth()) { Text("🤔  " + stringResource(R.string.games_wyr)) }
                    OutlinedButton(onClick = { screen = GameScreen.LOVE }, modifier = Modifier.fillMaxWidth()) { Text("💝  " + stringResource(R.string.games_love_lang)) }
                    OutlinedButton(onClick = { screen = GameScreen.QUIZZES }, modifier = Modifier.fillMaxWidth()) { Text("🧩  " + stringResource(R.string.games_know_me)) }
                }
            },
        )
        GameScreen.THIRTY_SIX -> CardStepDialog(
            title = "💞  " + stringResource(R.string.games_36),
            hint = stringResource(R.string.games_36_hint),
            cards = stringArrayResource(R.array.games_36_questions).toList(),
            onBack = back,
        )
        GameScreen.WYR -> WouldYouRatherDialog(onBack = back)
        GameScreen.LOVE -> LoveLanguageDialog(onBack = back)
        GameScreen.QUIZZES -> QuizzesDialog(quizzes, back, onCreateQuiz, onLoadQuiz, onSubmitQuiz, onDeleteQuiz)
    }
}

/** Steps through a list of cards one at a time, with Back/Next and a progress counter. */
@Composable
private fun CardStepDialog(title: String, hint: String?, cards: List<String>, onBack: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    MenchDialog(
        onDismissRequest = onBack,
        confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.us_close)) } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.games_progress, index + 1, cards.size), style = MaterialTheme.typography.labelMedium)
                Card(modifier = Modifier.fillMaxWidth().tiltOnTouch()) {
                    Text(cards[index], style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp).fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.games_prev)) }
                    Button(onClick = { index++ }, enabled = index < cards.lastIndex, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.games_next)) }
                }
            }
        },
    )
}

@Composable
private fun WouldYouRatherDialog(onBack: () -> Unit) {
    val pairs = stringArrayResource(R.array.games_wyr_pairs).map { it.split('|', limit = 2) }.filter { it.size == 2 }
    var index by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    MenchDialog(
        onDismissRequest = onBack,
        confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.us_close)) } },
        title = { Text("🤔  " + stringResource(R.string.games_wyr)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.games_wyr_pick), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.games_progress, index + 1, pairs.size), style = MaterialTheme.typography.labelMedium)
                listOf(0, 1).forEach { side ->
                    val selected = picked == side
                    Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth().pressScale(),
                        onClick = { picked = side },
                    ) {
                        Text(pairs[index][side], style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(18.dp).fillMaxWidth())
                    }
                }
                Button(onClick = { index++; picked = null }, enabled = index < pairs.lastIndex, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.games_next))
                }
            }
        },
    )
}

@Composable
private fun LoveLanguageDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    val questions = stringArrayResource(R.array.games_love_pairs).map { it.split('|', limit = 2) }.filter { it.size == 2 }
    val answers = remember { mutableStateListOf<Boolean>() }
    var history by remember { mutableStateOf(loveHistory(context)) }
    val done = answers.size >= minOf(questions.size, lovePairs.size)

    MenchDialog(
        onDismissRequest = onBack,
        confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.us_close)) } },
        dismissButton = if (done) ({ TextButton(onClick = { answers.clear() }) { Text(stringResource(R.string.lang_again)) } }) else null,
        title = { Text("💝  " + stringResource(R.string.games_love_lang)) },
        text = {
            if (!done) {
                val i = answers.size
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.games_progress, i + 1, questions.size), style = MaterialTheme.typography.labelMedium)
                    listOf(true, false).forEach { first ->
                        OutlinedButton(onClick = { answers.add(first) }, modifier = Modifier.fillMaxWidth().pressScale()) {
                            Text(if (first) questions[i][0] else questions[i][1], textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                val scores = remember(answers.size) { scoreLoveLanguages(answers.toList()) }
                val top = topLoveLanguages(scores)
                androidx.compose.runtime.LaunchedEffect(done) {
                    top.firstOrNull()?.let { saveLoveResult(context, it); history = loveHistory(context) }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SparkleBurst(size = 72.dp)
                    top.forEach { lang ->
                        Text(stringResource(R.string.lang_result, stringResource(lang.nameRes)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(stringResource(lang.descRes), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                    LoveLanguage.entries.forEach { lang ->
                        Text("${stringResource(lang.nameRes)}: ${scores[lang] ?: 0}", style = MaterialTheme.typography.bodySmall)
                    }
                    val older = history.drop(1)
                    if (older.isNotEmpty()) {
                        Text(stringResource(R.string.lang_history), style = MaterialTheme.typography.titleSmall)
                        older.forEach { (time, lang) ->
                            Text("${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))} — ${stringResource(lang.nameRes)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun QuizzesDialog(
    quizzes: List<QuizSummary>,
    onBack: () -> Unit,
    onCreateQuiz: (String, List<QuizQuestion>) -> Unit,
    onLoadQuiz: (String, (QuizDetail?) -> Unit) -> Unit,
    onSubmitQuiz: (String, List<Int>, (QuizResult?) -> Unit) -> Unit,
    onDeleteQuiz: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<QuizDetail?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    MenchDialog(
        onDismissRequest = onBack,
        confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.us_close)) } },
        dismissButton = { TextButton(onClick = { creating = true }) { Text(stringResource(R.string.quiz_new)) } },
        title = { Text("🧩  " + stringResource(R.string.games_know_me)) },
        text = {
            if (quizzes.isEmpty()) {
                EmptyHeartState(stringResource(R.string.quiz_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quizzes, key = { it.id }) { quiz ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(quiz.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(if (quiz.isMine) R.string.quiz_you_wrote else R.string.quiz_by_partner),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val status = when {
                                    quiz.isMine && quiz.partnerScore != null -> stringResource(R.string.quiz_partner_result, quiz.partnerScore, quiz.partnerTotal ?: quiz.questionCount)
                                    quiz.isMine -> stringResource(R.string.quiz_not_played)
                                    quiz.myScore != null -> stringResource(R.string.quiz_your_result, quiz.myScore, quiz.myTotal ?: quiz.questionCount)
                                    else -> null
                                }
                                if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!quiz.isMine && quiz.myScore == null) {
                                        Button(onClick = { onLoadQuiz(quiz.id) { detail -> if (detail != null) playing = detail } }) {
                                            Text(stringResource(R.string.quiz_start))
                                        }
                                    }
                                    if (quiz.isMine) {
                                        TextButton(onClick = { confirmDelete = quiz.id }) { Text(stringResource(R.string.counters_delete)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )

    confirmDelete?.let { id ->
        MenchDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.quiz_delete_confirm)) },
            confirmButton = { TextButton(onClick = { onDeleteQuiz(id); confirmDelete = null }) { Text(stringResource(R.string.counters_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.us_cancel)) } },
        )
    }
    if (creating) CreateQuizDialog(onDismiss = { creating = false }, onPublish = { title, questions -> onCreateQuiz(title, questions); creating = false })
    playing?.let { detail -> TakeQuizDialog(detail, onSubmitQuiz, onDone = { playing = null }) }
}

@Composable
private fun CreateQuizDialog(onDismiss: () -> Unit, onPublish: (String, List<QuizQuestion>) -> Unit) {
    var title by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf(QuizDraft()) }
    val built = drafts.map { buildQuestion(it) }
    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quiz_new)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { if (it.length <= 80) title = it }, label = { Text(stringResource(R.string.quiz_title_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                drafts.forEachIndexed { qi, draft ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = draft.text,
                                onValueChange = { if (it.length <= 140) drafts[qi] = draft.copy(text = it) },
                                label = { Text(stringResource(R.string.quiz_question_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            draft.options.forEachIndexed { oi, option ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = draft.correct == oi, onClick = { drafts[qi] = draft.copy(correct = oi) })
                                    OutlinedTextField(
                                        value = option,
                                        onValueChange = { v -> if (v.length <= 80) drafts[qi] = draft.copy(options = draft.options.toMutableList().also { it[oi] = v }) },
                                        label = { Text(stringResource(R.string.quiz_option_hint, oi + 1)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            Text(stringResource(R.string.quiz_mark_correct), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (drafts.size < 5) TextButton(onClick = { drafts.add(QuizDraft()) }) { Text(stringResource(R.string.quiz_add_question)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && built.all { it != null },
                onClick = { onPublish(title.trim(), built.filterNotNull()) },
            ) { Text(stringResource(R.string.quiz_publish)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.us_cancel)) } },
    )
}

@Composable
private fun TakeQuizDialog(detail: QuizDetail, onSubmit: (String, List<Int>, (QuizResult?) -> Unit) -> Unit, onDone: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    val answers = remember { mutableStateListOf<Int>() }
    var result by remember { mutableStateOf<QuizResult?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val finishedAnswering = answers.size >= detail.questions.size

    MenchDialog(
        onDismissRequest = onDone,
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.us_close)) } },
        title = { Text(detail.title) },
        text = {
            val res = result
            if (res != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (res.score == res.total) SparkleBurst(size = 72.dp)
                    Text(stringResource(R.string.quiz_result, res.score, res.total), style = MaterialTheme.typography.titleLarge)
                    if (res.score == res.total) Text(stringResource(R.string.quiz_perfect), textAlign = TextAlign.Center)
                }
            } else if (!finishedAnswering) {
                val q = detail.questions[index]
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.games_progress, index + 1, detail.questions.size), style = MaterialTheme.typography.labelMedium)
                    Text(q.text, style = MaterialTheme.typography.titleMedium)
                    q.options.forEachIndexed { oi, option ->
                        OutlinedButton(onClick = { answers.add(oi); index = minOf(index + 1, detail.questions.lastIndex) }, modifier = Modifier.fillMaxWidth().pressScale()) {
                            Text(option)
                        }
                    }
                }
            } else {
                Button(
                    enabled = !submitting,
                    onClick = {
                        submitting = true
                        onSubmit(detail.id, answers.toList()) { r -> submitting = false; result = r }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.quiz_submit)) }
            }
        },
    )
}
