package com.omidgame.mench.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.MenchSpacing

// Logo geometry on a 64x64 grid: a chat bubble (with tail at the bottom-start) holding a heart.
private const val BUBBLE_PATH =
    "M18,6 H46 A14,14 0 0 1 60,20 V36 A14,14 0 0 1 46,50 H28 L14,62 L16,50 H18 A14,14 0 0 1 4,36 V20 A14,14 0 0 1 18,6 Z"
private const val HEART_PATH =
    "M32,43 C32,43 17,34 17,24.5 C17,20 20.6,17 25,17 C28.2,17 30.8,18.7 32,21.6 C33.2,18.7 35.8,17 39,17 C43.4,17 47,20 47,24.5 C47,34 32,43 32,43 Z"

/**
 * The MENCH logo mark, drawn as pure vector paths (no bitmap, no resource parsing), so it
 * stays sharp at any size and follows the current accent through [MenchBrand.heroBrush].
 */
@Composable
fun MenchLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    bubble: Brush = MenchBrand.heroBrush(),
    heart: Color = Color.White,
) {
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val matrix = Matrix().apply { scale(this@drawWithCache.size.width / 64f, this@drawWithCache.size.height / 64f) }
                val bubblePath = PathParser().parsePathString(BUBBLE_PATH).toPath().apply { transform(matrix) }
                val heartPath = PathParser().parsePathString(HEART_PATH).toPath().apply { transform(matrix) }
                onDrawBehind {
                    drawPath(bubblePath, bubble)
                    drawPath(heartPath, heart)
                }
            },
    )
}

/** Mark plus the MENCH wordmark, for splash/onboarding/about screens. */
@Composable
fun MenchLogo(modifier: Modifier = Modifier, markSize: Dp = 88.dp) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MenchSpacing.md),
    ) {
        MenchLogoMark(size = markSize)
        Text(
            text = "MENCH",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
