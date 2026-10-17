package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.UserAvatar
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.feature.chat.domain.Counter
import com.omidgame.mench.feature.chat.domain.Letter
import com.omidgame.mench.feature.chat.domain.Memory
import com.omidgame.mench.feature.chat.domain.Question
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Phase 3 — the couple profile: one showpiece screen that gathers what the two of you have built together. */

data class CoupleProfile(
    /** Days since you got together; null if no date is set (or it is in the future). */
    val daysTogether: Long?,
    /** The next milestone (in days) and how many days away it is; null without a together date. */
    val nextMilestone: Pair<Long, Long>?,
    /** True when today itself is a milestone day. */
    val milestoneToday: Boolean,
    /** Days until you next meet; null if unset or already past. 0 means today. */
    val daysToNextMeet: Long?,
    val memories: Int,
    val letters: Int,
    /** Up to three counters worth showing: tallies first (biggest first), then date counters. */
    val featuredCounters: List<Counter>,
    val streak: Int,
    val bestStreak: Int,
)

internal fun buildCoupleProfile(
    today: LocalDate,
    togetherSinceIso: String?,
    nextMeetIso: String?,
    memories: List<Memory>,
    letters: List<Letter>,
    counters: List<Counter>,
    question: Question?,
): CoupleProfile {
    val days = togetherSinceIso
        ?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), today) }.getOrNull() }
        ?.takeIf { it >= 0 }
    val meet = nextMeetIso
        ?.let { runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) }.getOrNull() }
        ?.takeIf { it >= 0 }
    val featured = counters.sortedWith(
        compareByDescending<Counter> { it.kind == "tally" }.thenByDescending { it.tallyCount },
    ).take(3)
    return CoupleProfile(
        daysTogether = days,
        nextMilestone = days?.let { nextMilestone(it) },
        milestoneToday = days?.let { isMilestone(it) } ?: false,
        daysToNextMeet = meet,
        memories = memories.size,
        letters = letters.size,
        featuredCounters = featured,
        streak = question?.streak ?: 0,
        bestStreak = question?.bestStreak ?: 0,
    )
}

/** 1 at the top, fading to 0.25 after [FADE_DISTANCE_PX] of scrolling. */
internal fun headerAlpha(scrollPx: Float): Float = (1f - scrollPx / FADE_DISTANCE_PX).coerceIn(0.25f, 1f)

private const val FADE_DISTANCE_PX = 700f

@Composable
fun CoupleProfileDialog(
    profile: CoupleProfile,
    partnerId: String?,
    partnerAvatarVersion: Long?,
    partnerName: String?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val scroll = rememberScrollState()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                // Showpiece header: brand gradient, the partner's photo, a heart, and the big day count in a tilting glass card.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Parallax: the header drifts up at half speed and fades while you scroll the tiles over it.
                        .graphicsLayer {
                            translationY = scroll.value * 0.5f
                            alpha = headerAlpha(scroll.value.toFloat())
                        }
                        .background(MenchBrand.heroBrush())
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("💗", fontSize = 30.sp)
                        UserAvatar(userId = partnerId, version = partnerAvatarVersion, name = partnerName, size = 72.dp)
                    }
                    if (!partnerName.isNullOrBlank()) {
                        Text(partnerName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .gyroTilt(5f)
                            .tiltOnTouch(7f)
                            .glass(corner = 22.dp)
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (profile.daysTogether != null) {
                            RollingNumber(profile.daysTogether, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.profile_days_together), color = Color.White)
                            if (profile.milestoneToday) {
                                SparkleBurst(size = 72.dp, color = Color.White)
                                Text("🎉 " + stringResource(R.string.profile_milestone_today), color = Color.White)
                            } else {
                                profile.nextMilestone?.let { (target, left) ->
                                    Text(
                                        stringResource(R.string.counters_next_milestone, left, target),
                                        color = Color.White.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        } else {
                            Text(stringResource(R.string.profile_set_date), color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PartnerTimeTile()
                    BirthdayTile()
                    profile.daysToNextMeet?.let { d ->
                        ProfileTile("✈️", stringResource(if (d == 0L) R.string.profile_meet_today else R.string.profile_meet_in, d))
                    }
                    if (profile.streak > 0) {
                        ProfileTile("🔥", stringResource(R.string.question_streak, profile.streak))
                    }
                    ProfileTile("📸", stringResource(R.string.profile_memories, profile.memories))
                    ProfileTile("💌", stringResource(R.string.profile_letters, profile.letters))
                    profile.featuredCounters.forEach { c ->
                        val line = if (c.kind == "tally") "${c.title}: ${c.tallyCount}" else c.title
                        ProfileTile(c.emoji, line)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(bottom = 12.dp),
                ) { Text(stringResource(R.string.us_close)) }
            }
        }
    }
}

@Composable
private fun ProfileTile(emoji: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tiltOnTouch(4f)
            .glass(corner = 16.dp, tint = MaterialTheme.colorScheme.primary)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
