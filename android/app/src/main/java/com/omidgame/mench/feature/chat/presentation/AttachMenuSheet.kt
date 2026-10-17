package com.omidgame.mench.feature.chat.presentation

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.components.MenchSheet
import kotlinx.coroutines.delay

private data class AttachAction(val labelRes: Int, val icon: ImageVector, val onClick: () -> Unit)

/** True unless the user turned system animations off (Settings > Accessibility > Remove animations). */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    return remember {
        // Settings Phase 2: the in-app "reduced animations" choice also switches decorative animation off.
        val reducedByUser = context.applicationContext.getSharedPreferences("mench_animation", android.content.Context.MODE_PRIVATE)
            .getBoolean("reduced", false)
        !reducedByUser &&
            runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f
    }
}

/** Animated grid sheet behind the paperclip: file / photo, poll, location. Items pop in one after another. */
@Composable
fun AttachMenuSheet(
    onFile: () -> Unit,
    onGallery: () -> Unit = {},
    onTogether: (() -> Unit)? = null,
    onPoll: () -> Unit,
    onLocation: () -> Unit,
    onContact: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = listOfNotNull(
        AttachAction(R.string.attach_gallery, Icons.Filled.PhotoLibrary, onGallery),
        AttachAction(R.string.attach_file, Icons.Filled.InsertDriveFile, onFile),
        AttachAction(R.string.attach_poll, Icons.Filled.Poll, onPoll),
        AttachAction(R.string.attach_location, Icons.Filled.LocationOn, onLocation),
        AttachAction(R.string.attach_contact, Icons.Filled.Person, onContact),
        onTogether?.let { AttachAction(R.string.attach_together, Icons.Filled.Movie, it) },
    )
    val animate = animationsEnabled()
    MenchSheet(onDismissRequest = onDismiss) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(actions, key = { _, a -> a.labelRes }) { index, action ->
                var shown by remember { mutableStateOf(!animate) }
                LaunchedEffect(Unit) {
                    if (animate) {
                        delay(index * 55L)
                        shown = true
                    }
                }
                val scale by animateFloatAsState(
                    targetValue = if (shown) 1f else 0.6f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "attachScale",
                )
                val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "attachAlpha")
                Column(
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                        .clip(MaterialTheme.shapes.large)
                        .clickable {
                            action.onClick()
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(
                        text = stringResource(action.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Box(modifier = Modifier.padding(bottom = 16.dp))
    }
}
