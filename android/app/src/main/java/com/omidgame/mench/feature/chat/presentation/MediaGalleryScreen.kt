package com.omidgame.mench.feature.chat.presentation

import androidx.compose.foundation.background
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.menchTopBarColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omidgame.mench.R
import com.omidgame.mench.core.network.AttachmentUrls
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import com.omidgame.mench.feature.chat.domain.Message
import com.omidgame.mench.feature.chat.domain.MessageAttachment
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    state: MediaGalleryUiState,
    onTabSelected: (MediaGalleryTab) -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = menchTopBarColors(),
                title = { Text(stringResource(R.string.media_gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == MediaGalleryTab.MEDIA,
                    onClick = { onTabSelected(MediaGalleryTab.MEDIA) },
                    text = { Text(stringResource(R.string.media_gallery_tab_media)) },
                )
                Tab(
                    selected = state.selectedTab == MediaGalleryTab.FILES,
                    onClick = { onTabSelected(MediaGalleryTab.FILES) },
                    text = { Text(stringResource(R.string.media_gallery_tab_files)) },
                )
            }

            when (state.selectedTab) {
                MediaGalleryTab.MEDIA -> MediaGrid(state.mediaMessages)
                MediaGalleryTab.FILES -> FileList(state.fileMessages)
            }
        }
    }
}

@Composable
private fun MediaGrid(messages: List<Message>) {
    if (messages.isEmpty()) {
        EmptyState(stringResource(R.string.media_gallery_empty_media))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(messages, key = { it.clientMsgId }) { message ->
            MediaThumbnail(message)
        }
    }
}

@Composable
private fun MediaThumbnail(message: Message) {
    val attachment = message.attachment ?: return
    val model = when (message.kind) {
        DomainMessageKind.VIDEO -> attachment.localThumbnailUri
            ?: attachment.id?.let { AttachmentUrls.thumbnail(it) }
        else -> attachment.localContentUri
            ?: attachment.id?.let { AttachmentUrls.thumbnail(it) }
    }

    Box(modifier = Modifier.aspectRatio(1f).padding(1.dp)) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (message.kind == DomainMessageKind.VIDEO) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = stringResource(R.string.chat_video_call),
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun FileList(messages: List<Message>) {
    if (messages.isEmpty()) {
        EmptyState(stringResource(R.string.media_gallery_empty_files))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(messages, key = { it.clientMsgId }) { message ->
            FileRow(message.attachment ?: return@items, message.kind)
        }
    }
}

@Composable
private fun FileRow(attachment: MessageAttachment, kind: DomainMessageKind) {
    ListItem(
        headlineContent = { Text(attachment.originalFilename) },
        supportingContent = { Text(formatBytes(attachment.sizeBytes)) },
        leadingContent = {
            Icon(
                imageVector = if (kind == DomainMessageKind.AUDIO) Icons.Filled.AudioFile else Icons.Filled.InsertDriveFile,
                contentDescription = null,
            )
        },
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Same convention as feature/settings/presentation/DataStorageScreen's
// private formatBytes — kept file-local rather than shared, matching
// that file's existing precedent for this small a helper.
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exponent)
    return "%.1f %s".format(value, units[exponent - 1])
}
