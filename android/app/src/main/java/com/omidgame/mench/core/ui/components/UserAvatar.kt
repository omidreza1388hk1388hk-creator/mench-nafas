package com.omidgame.mench.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.omidgame.mench.BuildConfig

/**
 * A round profile photo. Loads `users/{id}/avatar?v={version}` through the
 * app's authenticated image loader (same as chat images); [version] is only
 * a cache-buster that changes when the person uploads a new photo. With no
 * photo (null version) it shows a tinted circle with the first letter of
 * [name], so lists never have holes.
 */
@Composable
fun UserAvatar(
    userId: String?,
    version: Long?,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    /** Phase 3C: same key on two screens makes the photo glide between them (see SharedElements.kt). */
    sharedKey: String? = null,
    /** Phase 3B: draw the story ring when this conversation (identified by [sharedKey]) has unseen stories. */
    showStoryRing: Boolean = false,
) {
    val ring = showStoryRing && sharedKey != null && sharedKey in LocalUnseenStories.current
    val ringModifier = if (ring) Modifier.border(3.dp, com.omidgame.mench.core.ui.theme.MenchBrand.heroBrush(), CircleShape) else Modifier
    val base = modifier.sharedAvatar(sharedKey).size(size).clip(CircleShape).then(ringModifier)
    if (userId != null && version != null) {
        AsyncImage(
            model = "${BuildConfig.API_BASE_URL}users/$userId/avatar?v=$version",
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
    } else {
        Box(
            modifier = base.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name?.trim()?.firstOrNull()?.toString() ?: "💗",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}
