package com.omidgame.mench.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.core.media.AttachmentCache
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
)

@HiltViewModel
class DataStorageViewModel @Inject constructor(
    private val attachmentCache: AttachmentCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataStorageUiState())
    val uiState: StateFlow<DataStorageUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val size = attachmentCache.totalCacheSizeBytes()
            _uiState.update { it.copy(isLoading = false, cacheSizeBytes = size) }
        }
    }

    fun onClearCache() {
        _uiState.update { it.copy(isClearing = true) }
        viewModelScope.launch {
            attachmentCache.clearAll()
            val size = attachmentCache.totalCacheSizeBytes()
            _uiState.update { it.copy(isClearing = false, cacheSizeBytes = size) }
        }
    }
}
