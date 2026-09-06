package com.omidgame.mench.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

val MenchShapes = Shapes(
    small = RoundedCornerShape(8.dp()),
    medium = RoundedCornerShape(14.dp()),
    large = RoundedCornerShape(24.dp()),
)

// Small local helper so this file has no import ambiguity with
// androidx.compose.ui.unit.dp when used outside a Composable scope.
private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
