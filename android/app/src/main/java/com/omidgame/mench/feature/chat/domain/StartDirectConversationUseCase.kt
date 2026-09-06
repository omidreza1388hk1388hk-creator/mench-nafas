package com.omidgame.mench.feature.chat.domain

import javax.inject.Inject

class StartDirectConversationUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(targetPhoneE164: String): ChatResult<Conversation> {
        if (!targetPhoneE164.startsWith("+") || targetPhoneE164.length < 8) {
            return ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
        return chatRepository.startDirectConversation(targetPhoneE164)
    }
}
