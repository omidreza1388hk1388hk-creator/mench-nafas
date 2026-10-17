package com.omidgame.mench.core.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.MenchDuration
import com.omidgame.mench.core.ui.theme.MenchElevation
import com.omidgame.mench.core.ui.theme.MenchRadius
import com.omidgame.mench.core.ui.theme.MenchSpacing
import com.omidgame.mench.core.ui.theme.rememberReduceMotion

private const val DIALOG_BLUR_RADIUS_PX = 24

/**
 * MENCH's replacement for Material's AlertDialog. The parameter list intentionally mirrors
 * `AlertDialog` (same names, same slots) so every existing call site only needed its import
 * and function name changed.
 *
 * Look: frosted-glass card with a hairline border, a thin brand-gradient strip on top, a
 * spring scale-in, and (Android 12+) a real blur of the content behind the dialog window.
 * Below Android 12 the card is simply a little more opaque instead of blurred.
 */
@Composable
fun MenchDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(MenchRadius.xl),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    val reduceMotion = rememberReduceMotion()
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        // Blur whatever is behind the dialog window (Android 12+). The window attribute is
        // only touched on API 31+, so lower versions never reach the blur fields.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = window.attributes
                attrs.blurBehindRadius = DIALOG_BLUR_RADIUS_PX
                window.attributes = attrs
            }
        }

        AnimatedVisibility(
            visibleState = visibleState,
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                fadeIn(tween(MenchDuration.FAST)) +
                    scaleIn(animationSpec = MenchMotion.bouncy(), initialScale = 0.88f)
            },
        ) {
            Surface(
                modifier = modifier
                    .padding(horizontal = MenchSpacing.xl)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                shape = shape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (blurSupported) 0.86f else 0.98f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = MenchElevation.none,
                shadowElevation = MenchElevation.overlay,
                border = BorderStroke(1.dp, MenchBrand.hairline()),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MenchBrand.heroBrush()),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = MenchSpacing.xl, end = MenchSpacing.xl, top = MenchSpacing.xl, bottom = MenchSpacing.md),
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = MenchSpacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { icon() }
                            }
                        }
                        if (title != null) {
                            Box(modifier = Modifier.padding(bottom = MenchSpacing.md)) {
                                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) { title() }
                            }
                        }
                        if (text != null) {
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = MenchSpacing.md, end = MenchSpacing.md, bottom = MenchSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(MenchSpacing.xs, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissButton != null) dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}
