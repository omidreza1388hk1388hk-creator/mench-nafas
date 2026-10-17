package com.omidgame.mench.feature.chat.presentation

import com.omidgame.mench.feature.chat.domain.Message

enum class MediaGalleryTab { MEDIA, FILES }

data class MediaGalleryUiState(
    val selectedTab: MediaGalleryTab = MediaGalleryTab.MEDIA,
    /** IMAGE + VIDEO kind messages, newest first — rendered as a thumbnail grid. */
    val mediaMessages: List<Message> = emptyList(),
    /** FILE + AUDIO kind messages, newest first — rendered as a row list. */
    val fileMessages: List<Message> = emptyList(),
)
