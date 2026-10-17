package com.omidgame.mench.feature.chat.presentation

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.media.AutoDownloadSettings
import com.omidgame.mench.core.media.MediaKind
import com.omidgame.mench.core.network.NetworkKind
import com.omidgame.mench.core.network.currentNetworkKind
import com.omidgame.mench.feature.chat.domain.MessageAttachment

/**
 * Phase 1B leftover: enforces the auto-download rules inside message bubbles. A received photo/video
 * whose rule says "do not auto-download" shows a tap-to-download placeholder instead of fetching
 * its thumbnail; the user's own media and anything already on the device always show.
 */
@Stable
class MediaGate(val allowed: Boolean, val allow: () -> Unit)

@Composable
fun rememberMediaGate(conversationId: String, attachment: MessageAttachment, kind: MediaKind): MediaGate {
    val context = LocalContext.current.applicationContext
    // Survives scrolling the bubble off-screen and back, so a manual download stays downloaded.
    var userAllowed by rememberSaveable(attachment.id, attachment.localContentUri) { mutableStateOf(false) }
    val onDevice = attachment.localContentUri != null || attachment.localThumbnailUri != null
    val auto = remember(conversationId, attachment.id, attachment.sizeBytes) {
        val network = currentNetworkKind(context)
        // Offline: let Coil try its disk cache rather than hiding an image that may already be cached.
        network == NetworkKind.NONE ||
            AutoDownloadSettings(context).shouldAutoDownload(conversationId, kind, attachment.sizeBytes, network)
    }
    return MediaGate(allowed = onDevice || userAllowed || auto, allow = { userAllowed = true })
}

@Composable
fun MediaDownloadPlaceholder(attachment: MessageAttachment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ratio = if ((attachment.widthPx ?: 0) > 0 && (attachment.heightPx ?: 0) > 0) {
        (attachment.widthPx!!.toFloat() / attachment.heightPx!!.toFloat()).coerceIn(0.6f, 1.8f)
    } else 1.33f
    Box(
        modifier = modifier
            .widthIn(max = 260.dp)
            .aspectRatio(ratio)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                Icons.Filled.Download,
                contentDescription = stringResource(R.string.media_tap_to_download),
                modifier = Modifier.size(32.dp),
            )
            Text(
                Formatter.formatShortFileSize(context, attachment.sizeBytes),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
