package com.omidgame.mench.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import com.omidgame.mench.feature.appearance.domain.BubbleStyle

fun menchShapes(style: BubbleStyle = BubbleStyle.ROUNDED): Shapes = when (style) {
    BubbleStyle.ROUNDED -> Shapes(
        small = RoundedCornerShape(8.dp()),
        medium = RoundedCornerShape(14.dp()),
        large = RoundedCornerShape(24.dp()),
    )
    // A visibly flatter, more angular family — not a tiny tweak of the
    // rounded values — so the two options read as distinct choices in
    // the appearance picker's live preview, not a rounding-error apart.
    BubbleStyle.SQUARED -> Shapes(
        small = RoundedCornerShape(2.dp()),
        medium = RoundedCornerShape(4.dp()),
        large = RoundedCornerShape(8.dp()),
    )
    // Capsule-like: the corner radius is generous at every size.
    BubbleStyle.PILL -> Shapes(
        small = RoundedCornerShape(16.dp()),
        medium = RoundedCornerShape(24.dp()),
        large = RoundedCornerShape(36.dp()),
    )
    // Asymmetric: one corner stays sharp (the bottom "end" corner), like a leaf. Direction-aware, so it mirrors in RTL.
    BubbleStyle.LEAF -> Shapes(
        small = RoundedCornerShape(topStart = 10.dp(), topEnd = 10.dp(), bottomEnd = 2.dp(), bottomStart = 10.dp()),
        medium = RoundedCornerShape(topStart = 18.dp(), topEnd = 18.dp(), bottomEnd = 3.dp(), bottomStart = 18.dp()),
        large = RoundedCornerShape(topStart = 28.dp(), topEnd = 28.dp(), bottomEnd = 4.dp(), bottomStart = 28.dp()),
    )
}

// Preserved default for any call site still referencing the old fixed value.
val MenchShapes: Shapes = menchShapes()

// Small local helper so this file has no import ambiguity with
// androidx.compose.ui.unit.dp when used outside a Composable scope.
private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
