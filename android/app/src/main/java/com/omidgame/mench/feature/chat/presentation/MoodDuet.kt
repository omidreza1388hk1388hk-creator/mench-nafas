package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Phase 3A — "daily mood duet": both moods are blended into one shared gradient. */

/** One colour per mood emoji; unknown emoji get a soft rose so a future mood never breaks the banner. */
internal fun moodColor(emoji: String): Color = when (emoji) {
    "🥰" -> Color(0xFFFF6F91)
    "😍" -> Color(0xFFFF4D8D)
    "😊" -> Color(0xFFFFC857)
    "😎" -> Color(0xFF38A3F5)
    "😴" -> Color(0xFF7E6BD9)
    "🥱" -> Color(0xFF9FA8DA)
    "😢" -> Color(0xFF4F83CC)
    "😡" -> Color(0xFFE5484D)
    "🤒" -> Color(0xFF7BC47F)
    "💪" -> Color(0xFFFF8A3D)
    else -> Color(0xFFFF9EB5)
}

/** The shared gradient for two moods: your colour on the start side flowing into your partner's on the end side. */
internal fun duetColors(mine: String, other: String): List<Color> = listOf(moodColor(mine), moodColor(other))

@Composable
fun MoodDuetBanner(mineEmoji: String, otherEmoji: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(duetColors(mineEmoji, otherEmoji)))
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mineEmoji, fontSize = 30.sp)
        Text("💞", fontSize = 26.sp)
        Text(otherEmoji, fontSize = 30.sp)
    }
}
