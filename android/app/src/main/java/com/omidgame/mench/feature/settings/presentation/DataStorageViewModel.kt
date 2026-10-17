package com.omidgame.mench.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import com.omidgame.mench.core.media.AttachmentCache
import com.omidgame.mench.core.media.AutoDownloadSettings
import com.omidgame.mench.core.media.MediaKind
import com.omidgame.mench.core.network.NetworkKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataStorageUiState(
    val isLoading: Boolean = true,
    val cacheSizeBytes: Long = 0L,
    val isClearing: Boolean = false,
    /** Phase 1B: Coil's on-disk image cache (thumbnails and previews), separate from attachment files. */
    val imageCacheBytes: Long = 0L,
    /** Phase 1B: rules[network][kind] = auto-download enabled. */
    val rules: Map<NetworkKind, Set<MediaKind>> = emptyMap(),
    val maxMobileSizeMb: Int = 10,
    /** Phase 1D.5: bytes that cleanup of each type would really free (unsent files excluded). */
    val freeable: Map<AttachmentCache.CacheType, Long> = emptyMap(),
)

@HiltViewModel
class DataStorageViewModel @Inject constructor(
    private val attachmentCache: AttachmentCache,
    private val outboxDao: com.omidgame.mench.core.database.OutboxDao,
    private val imageLoader: ImageLoader,
    private val autoDownload: AutoDownloadSettings,
) : ViewModel() {

    private val networks = listOf(NetworkKind.WIFI, NetworkKind.MOBILE, NetworkKind.ROAMING)

    private fun currentRules(): Map<NetworkKind, Set<MediaKind>> =
        networks.associateWith { net -> MediaKind.entries.filter { autoDownload.isEnabled(net, it) }.toSet() }

    @OptIn(ExperimentalCoilApi::class)
    private fun imageCacheSize(): Long = imageLoader.diskCache?.size ?: 0L

    /** Frees one media type only. The caller has already shown the exact size to the user. */
    fun onClearType(type: AttachmentCache.CacheType) {
        _uiState.update { it.copy(isClearing = true) }
        viewModelScope.launch {
            val payloads = outboxDao.allPayloads()
            attachmentCache.clearType(type, payloads)
            val size = attachmentCache.totalCacheSizeBytes()
            _uiState.update { it.copy(isClearing = false, cacheSizeBytes = size, freeable = attachmentCache.freeableBytes(payloads)) }
        }
    }

    fun onToggleRule(network: NetworkKind, kind: MediaKind) {
        autoDownload.setEnabled(network, kind, !autoDownload.isEnabled(network, kind))
        _uiState.update { it.copy(rules = currentRules()) }
    }

    fun onMaxMobileSizeChanged(mb: Int) {
        autoDownload.setMaxMobileSizeMb(mb)
        _uiState.update { it.copy(maxMobileSizeMb = autoDownload.maxMobileSizeMb()) }
    }

    /** Clears only Coil's thumbnail/preview cache. Messages and server data are untouched. */
    @OptIn(ExperimentalCoilApi::class)
    fun onClearImageCache() {
        _uiState.update { it.copy(isClearing = true) }
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                imageLoader.diskCache?.clear()
                imageLoader.memoryCache?.clear()
            }
            _uiState.update { it.copy(isClearing = false, imageCacheBytes = imageCacheSize()) }
        }
    }

    private val _uiState = MutableStateFlow(DataStorageUiState())
    val uiState: StateFlow<DataStorageUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val size = attachmentCache.totalCacheSizeBytes()
            val imageSize = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { imageCacheSize() }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    cacheSizeBytes = size,
                    imageCacheBytes = imageSize,
                    rules = currentRules(),
                    maxMobileSizeMb = autoDownload.maxMobileSizeMb(),
                    freeable = attachmentCache.freeableBytes(outboxDao.allPayloads()),
                )
            }
        }
    }

    fun onClearCache() {
        _uiState.update { it.copy(isClearing = true) }
        viewModelScope.launch {
            attachmentCache.clearAll(outboxDao.allPayloads())
            val size = attachmentCache.totalCacheSizeBytes()
            _uiState.update { it.copy(isClearing = false, cacheSizeBytes = size, freeable = attachmentCache.freeableBytes(outboxDao.allPayloads())) }
        }
    }
}
