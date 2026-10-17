package com.omidgame.mench.feature.chat.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Deliberately reuses [ChatRepository.observeMessages] rather than adding
 * a new repository/DAO query — this conversation's messages are already
 * loaded and kept live for [ChatViewModel]; filtering that same Flow by
 * kind here means a media send/delete shows up in the gallery the same
 * instant it does in the chat itself, with no second source of truth to
 * keep in sync.
 */
@HiltViewModel
class MediaGalleryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    chatRepository: ChatRepository,
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _selectedTab = MutableStateFlow(MediaGalleryTab.MEDIA)

    val uiState: StateFlow<MediaGalleryUiState> = combine(
        chatRepository.observeMessages(conversationId),
        _selectedTab,
    ) { messages, selectedTab ->
        val withAttachments = messages
            .filter { it.attachment != null && it.deletedAtEpochMillis == null }
            .sortedByDescending { it.createdAtEpochMillis }
        MediaGalleryUiState(
            selectedTab = selectedTab,
            mediaMessages = withAttachments.filter {
                (it.kind == DomainMessageKind.IMAGE && it.attachment?.isViewOnce() != true) || it.kind == DomainMessageKind.VIDEO
            },
            fileMessages = withAttachments.filter {
                it.kind == DomainMessageKind.FILE || it.kind == DomainMessageKind.AUDIO
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaGalleryUiState())

    fun onTabSelected(tab: MediaGalleryTab) {
        _selectedTab.update { tab }
    }
}
