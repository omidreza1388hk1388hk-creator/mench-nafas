package com.omidgame.mench.feature.settings.presentation

import androidx.compose.foundation.background
import com.omidgame.mench.core.ui.theme.MenchBrand
import com.omidgame.mench.core.ui.theme.menchTopBarColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DataStorageScreen(
    state: DataStorageUiState,
    onClearCache: () -> Unit,
    onBackClick: () -> Unit,
    onClearImageCache: () -> Unit = {},
    onToggleRule: (com.omidgame.mench.core.network.NetworkKind, com.omidgame.mench.core.media.MediaKind) -> Unit = { _, _ -> },
    onMaxMobileSizeChanged: (Int) -> Unit = {},
    onClearType: (com.omidgame.mench.core.media.AttachmentCache.CacheType) -> Unit = {},
) {
    var pendingClear by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.omidgame.mench.core.media.AttachmentCache.CacheType?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MenchBrand.heroBrush()),
                colors = menchTopBarColors(),
                title = { Text(stringResource(R.string.settings_data_storage)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.data_storage_cache_size, formatBytes(state.cacheSizeBytes)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.data_storage_cache_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (state.isClearing) {
                CircularProgressIndicator()
            } else {
                OutlinedButton(
                    onClick = onClearCache,
                    enabled = state.cacheSizeBytes > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.data_storage_clear_cache))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            BackupSection()

            // ---- Phase 1D.5: selective cleanup by media type (never touches messages or the server) ----
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.cleanup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.cleanup_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                com.omidgame.mench.core.media.AttachmentCache.CacheType.PHOTOS to R.string.auto_download_photos,
                com.omidgame.mench.core.media.AttachmentCache.CacheType.VIDEOS to R.string.auto_download_videos,
                com.omidgame.mench.core.media.AttachmentCache.CacheType.FILES to R.string.auto_download_files,
                com.omidgame.mench.core.media.AttachmentCache.CacheType.VOICE to R.string.auto_download_voice,
            ).forEach { (type, label) ->
                val bytes = state.freeable[type] ?: 0L
                OutlinedButton(
                    onClick = { pendingClear = type },
                    enabled = bytes > 0 && !state.isClearing,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(stringResource(label) + " - " + formatBytes(bytes)) }
            }

            // ---- Phase 1B: thumbnail/preview cache ----
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.data_storage_image_cache_size, formatBytes(state.imageCacheBytes)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onClearImageCache,
                enabled = state.imageCacheBytes > 0 && !state.isClearing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.data_storage_clear_image_cache))
            }

            // ---- Phase 1B: auto-download rules ----
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.auto_download_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.auto_download_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val networkRows = listOf(
                com.omidgame.mench.core.network.NetworkKind.WIFI to R.string.auto_download_wifi,
                com.omidgame.mench.core.network.NetworkKind.MOBILE to R.string.auto_download_mobile,
                com.omidgame.mench.core.network.NetworkKind.ROAMING to R.string.auto_download_roaming,
            )
            val kindLabels = listOf(
                com.omidgame.mench.core.media.MediaKind.PHOTO to R.string.auto_download_photos,
                com.omidgame.mench.core.media.MediaKind.VIDEO to R.string.auto_download_videos,
                com.omidgame.mench.core.media.MediaKind.FILE to R.string.auto_download_files,
                com.omidgame.mench.core.media.MediaKind.VOICE to R.string.auto_download_voice,
            )
            networkRows.forEach { (network, labelRes) ->
                Spacer(modifier = Modifier.height(14.dp))
                Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    kindLabels.forEach { (kind, kindRes) ->
                        androidx.compose.material3.FilterChip(
                            selected = state.rules[network]?.contains(kind) == true,
                            onClick = { onToggleRule(network, kind) },
                            label = { Text(stringResource(kindRes)) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.auto_download_max_mobile, state.maxMobileSizeMb),
                style = MaterialTheme.typography.labelLarge,
            )
            androidx.compose.material3.Slider(
                value = state.maxMobileSizeMb.toFloat(),
                onValueChange = { onMaxMobileSizeChanged(it.toInt()) },
                valueRange = 1f..100f,
            )
        }
    }
    pendingClear?.let { type ->
        val bytes = state.freeable[type] ?: 0L
        com.omidgame.mench.core.ui.components.MenchDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text(stringResource(R.string.cleanup_confirm_title)) },
            text = { Text(stringResource(R.string.cleanup_confirm_body, formatBytes(bytes))) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onClearType(type); pendingClear = null }) { Text(stringResource(R.string.cleanup_confirm_action)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingClear = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exponent)
    return "%.1f %s".format(value, units[exponent - 1])
}
