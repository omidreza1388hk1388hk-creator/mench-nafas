package com.omidgame.mench.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.MenchElevation
import com.omidgame.mench.core.ui.theme.MenchRadius
import com.omidgame.mench.core.ui.theme.MenchSpacing

/**
 * MENCH popup menu: rounded, hairline-bordered, soft shadow. Same call shape as
 * `DropdownMenu`. Pair with [MenchMenuItem].
 */
@Composable
fun MenchMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 200.dp),
        offset = offset,
        shape = RoundedCornerShape(MenchRadius.lg),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = MenchElevation.none,
        shadowElevation = MenchElevation.overlay,
        border = BorderStroke(1.dp, MenchBrand.hairline()),
        content = content,
    )
}

/** One row of a [MenchMenu]. Mirrors `DropdownMenuItem` (text / leadingIcon slots). */
@Composable
fun MenchMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .padding(horizontal = MenchSpacing.sm)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(MenchRadius.md))
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = MenchSpacing.md, vertical = MenchSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(MenchSpacing.md))
            }
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() }
        }
    }
}
