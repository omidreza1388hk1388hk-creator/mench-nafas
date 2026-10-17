package com.omidgame.mench.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.omidgame.mench.feature.chat.presentation.pressScale

/**
 * Phase 3B — MENCH's own small icon set for the couple features, drawn in code (24×24, single colour, tinted by
 * the surrounding content colour) so they match each other and the theme instead of mixing emoji and Material icons.
 */
object MenchIcons {
    private fun icon(name: String, evenOdd: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                pathBuilder = block,
            )
        }.build()

    /** Appends a full circle (two half-arcs) centred at ([cx], [cy]). */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx, cy - r)
        arcTo(r, r, 0f, true, true, cx, cy + r)
        arcTo(r, r, 0f, true, true, cx, cy - r)
        close()
    }

    val Heart: ImageVector by lazy {
        icon("MenchHeart") {
            moveTo(12f, 21f)
            lineTo(10.5f, 19.6f)
            curveTo(5.4f, 15f, 2f, 11.9f, 2f, 8.2f)
            curveTo(2f, 5.2f, 4.3f, 3f, 7.2f, 3f)
            curveTo(8.9f, 3f, 10.6f, 3.8f, 12f, 5.1f)
            curveTo(13.4f, 3.8f, 15.1f, 3f, 16.8f, 3f)
            curveTo(19.7f, 3f, 22f, 5.2f, 22f, 8.2f)
            curveTo(22f, 11.9f, 18.6f, 15f, 13.5f, 19.6f)
            close()
        }
    }

    /** Counters: a hash sign. */
    val Counter: ImageVector by lazy {
        icon("MenchCounter") {
            moveTo(8f, 3f); horizontalLineTo(10f); verticalLineTo(21f); horizontalLineTo(8f); close()
            moveTo(14f, 3f); horizontalLineTo(16f); verticalLineTo(21f); horizontalLineTo(14f); close()
            moveTo(3f, 8f); horizontalLineTo(21f); verticalLineTo(10f); horizontalLineTo(3f); close()
            moveTo(3f, 14f); horizontalLineTo(21f); verticalLineTo(16f); horizontalLineTo(3f); close()
        }
    }

    /** Shared canvas: a frame with a zig-zag stroke inside. */
    val Canvas: ImageVector by lazy {
        icon("MenchCanvas", evenOdd = true) {
            moveTo(3f, 3f); horizontalLineTo(21f); verticalLineTo(21f); horizontalLineTo(3f); close()
            moveTo(5f, 5f); verticalLineTo(19f); horizontalLineTo(19f); verticalLineTo(5f); close()
            moveTo(7f, 16f); lineTo(10.5f, 9f); lineTo(13.5f, 14f); lineTo(16f, 8f); lineTo(17.6f, 8.8f)
            lineTo(14f, 17f); lineTo(10.7f, 12.4f); lineTo(8.6f, 16.8f); close()
        }
    }

    /** Stories: a ring with a dot, like a story avatar. */
    val Story: ImageVector by lazy {
        icon("MenchStory", evenOdd = true) {
            circle(12f, 12f, 10f)
            circle(12f, 12f, 8f)
            circle(12f, 12f, 4.5f)
        }
    }

    /** Letters: an envelope outline with its flap. */
    val Letter: ImageVector by lazy {
        icon("MenchLetter", evenOdd = true) {
            moveTo(3f, 5f); horizontalLineTo(21f); verticalLineTo(19f); horizontalLineTo(3f); close()
            moveTo(5f, 7f); verticalLineTo(17f); horizontalLineTo(19f); verticalLineTo(7f); close()
            moveTo(4.5f, 8f); lineTo(5.6f, 6.6f); lineTo(12f, 12f); lineTo(18.4f, 6.6f); lineTo(19.5f, 8f); lineTo(12f, 14.4f); close()
        }
    }

    /** Vault: a padlock. */
    val Vault: ImageVector by lazy {
        icon("MenchVault", evenOdd = true) {
            moveTo(5f, 10f); horizontalLineTo(19f); verticalLineTo(21f); horizontalLineTo(5f); close()
            moveTo(8f, 10f); verticalLineTo(7f)
            arcTo(4f, 4f, 0f, false, true, 16f, 7f)
            verticalLineTo(10f); horizontalLineTo(14f); verticalLineTo(7f)
            arcTo(2f, 2f, 0f, false, false, 10f, 7f)
            verticalLineTo(10f); close()
            circle(12f, 15f, 1.6f)
        }
    }

    /** Couple profile: two heads and shoulders overlapping. */
    val Couple: ImageVector by lazy {
        icon("MenchCouple") {
            circle(8.5f, 8f, 3f)
            circle(15.5f, 8f, 3f)
            moveTo(2f, 20f)
            curveTo(2f, 15.5f, 5f, 13f, 8.5f, 13f)
            curveTo(10.4f, 13f, 12f, 13.7f, 12f, 13.7f)
            curveTo(12f, 13.7f, 13.6f, 13f, 15.5f, 13f)
            curveTo(19f, 13f, 22f, 15.5f, 22f, 20f)
            close()
        }
    }
}

/** A full-width outlined button with one of the [MenchIcons] and a label; gives a small press-scale response. */
@Composable
fun FeatureButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().pressScale()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label)
        }
    }
}
