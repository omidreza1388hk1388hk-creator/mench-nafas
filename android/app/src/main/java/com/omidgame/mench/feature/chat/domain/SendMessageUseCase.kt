package com.omidgame.mench.feature.chat.domain

import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(conversationId: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        chatRepository.sendMessage(conversationId, trimmed)
    }
}
