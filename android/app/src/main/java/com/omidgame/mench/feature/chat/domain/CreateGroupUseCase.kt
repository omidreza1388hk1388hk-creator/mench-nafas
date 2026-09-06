package com.omidgame.mench.feature.chat.domain

import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(title: String, memberPhoneNumbers: List<String>): ChatResult<Conversation> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            return ChatResult.Failure(ChatFailureReason.INVALID_GROUP)
        }
        // Client-side mirror of the same rule the backend enforces (see
        // ConversationsService.createGroup) — catching an obviously-empty
        // invite list here means the submit button can stay disabled
        // instead of round-tripping to the server just to learn that.
        val cleanedNumbers = memberPhoneNumbers.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanedNumbers.isEmpty()) {
            return ChatResult.Failure(ChatFailureReason.INVALID_GROUP)
        }
        return chatRepository.createGroup(trimmedTitle, cleanedNumbers)
    }
}
