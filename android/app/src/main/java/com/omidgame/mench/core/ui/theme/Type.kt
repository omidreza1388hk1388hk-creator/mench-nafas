package com.omidgame.mench.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Uses the system font for now; the MENCH custom type ramp is a Phase 6
// visual-identity deliverable. Sizes/weights follow Material3 defaults
// scaled slightly for a denser, "premium messenger" feel.
val MenchTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp),
)
