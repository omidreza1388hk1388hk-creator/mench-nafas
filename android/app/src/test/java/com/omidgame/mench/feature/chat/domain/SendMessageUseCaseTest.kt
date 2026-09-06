package com.omidgame.mench.feature.chat.domain

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SendMessageUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var useCase: SendMessageUseCase

    @Before
    fun setUp() {
        chatRepository = mockk(relaxUnitFun = true)
        useCase = SendMessageUseCase(chatRepository)
    }

    @Test
    fun `trims whitespace before sending`() = runTest {
        useCase("conv-1", "  hello there  ")
        coVerify { chatRepository.sendMessage("conv-1", "hello there") }
    }

    @Test
    fun `does not send a blank or whitespace-only message`() = runTest {
        useCase("conv-1", "   ")
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any()) }
    }

    @Test
    fun `does not send an empty message`() = runTest {
        useCase("conv-1", "")
        coVerify(exactly = 0) { chatRepository.sendMessage(any(), any()) }
    }
}
