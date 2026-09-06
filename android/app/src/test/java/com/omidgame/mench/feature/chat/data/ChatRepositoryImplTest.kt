package com.omidgame.mench.feature.chat.data

import com.google.common.truth.Truth.assertThat
import com.omidgame.mench.core.database.ConversationDao
import com.omidgame.mench.core.database.MessageDao
import com.omidgame.mench.core.database.MessageEntity
import com.omidgame.mench.core.database.OutboxDao
import com.omidgame.mench.core.database.OutboxEntity
import com.omidgame.mench.core.database.OutboxState
import com.omidgame.mench.core.database.UserDao
import com.omidgame.mench.core.database.UserEntity
import com.omidgame.mench.core.media.AttachmentCache
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.MarkReadBody
import com.omidgame.mench.core.network.realtime.RealtimeClient
import com.omidgame.mench.core.security.TokenStore
import com.omidgame.mench.core.work.OutboxSyncScheduler
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.match
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ChatRepositoryImplTest {

    private lateinit var chatApi: ChatApi
    private lateinit var conversationDao: ConversationDao
    private lateinit var messageDao: MessageDao
    private lateinit var outboxDao: OutboxDao
    private lateinit var userDao: UserDao
    private lateinit var tokenStore: TokenStore
    private lateinit var realtimeClient: RealtimeClient
    private lateinit var outboxSyncScheduler: OutboxSyncScheduler
    private lateinit var attachmentCache: AttachmentCache
    private lateinit var repository: ChatRepository

    private val selfUserId = "self-user-1"

    @Before
    fun setUp() {
        chatApi = mockk()
        conversationDao = mockk(relaxUnitFun = true)
        messageDao = mockk(relaxUnitFun = true)
        outboxDao = mockk(relaxUnitFun = true)
        userDao = mockk()
        tokenStore = mockk()
        realtimeClient = mockk(relaxUnitFun = true)
        outboxSyncScheduler = mockk(relaxUnitFun = true)
        attachmentCache = mockk(relaxUnitFun = true)

        every { realtimeClient.events } returns MutableSharedFlow()
        coEvery { userDao.getSelfOnce() } returns UserEntity(
            id = selfUserId,
            phoneE164 = "+15551234567",
            displayName = null,
            username = null,
            avatarUrl = null,
            updatedAtEpochMillis = 0L,
        )

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        repository = ChatRepositoryImpl(
            chatApi, conversationDao, messageDao, outboxDao, userDao,
            tokenStore, realtimeClient, outboxSyncScheduler, moshi, attachmentCache,
        )
    }

    @Test
    fun `sendAttachment copies content to app-private cache, writes a PENDING image message locally, and enqueues a durable outbox entry`() = runTest {
        coEvery { attachmentCache.copyToCache(any(), any()) } returns "/data/app/cache/fake-copy.jpg"
        every { attachmentCache.sizeOf("/data/app/cache/fake-copy.jpg") } returns 12345L

        repository.sendAttachment(
            conversationId = "conv-1",
            contentUri = "content://media/external/images/42",
            mimeType = "image/jpeg",
            originalFilename = "vacation.jpg",
            caption = "look at this",
        )

        coVerify { attachmentCache.copyToCache("content://media/external/images/42", ".jpg") }
        coVerify {
            messageDao.upsert(
                match<MessageEntity> {
                    it.conversationId == "conv-1" &&
                        it.senderId == selfUserId &&
                        it.kind == "image" &&
                        it.body == "look at this" &&
                        it.state == "PENDING" &&
                        it.attachmentId == null && // not yet known — assigned once upload succeeds
                        it.attachmentMimeType == "image/jpeg" &&
                        it.attachmentOriginalFilename == "vacation.jpg" &&
                        it.attachmentSizeBytes == 12345L &&
                        it.localContentUri == "/data/app/cache/fake-copy.jpg"
                },
            )
        }
        coVerify {
            outboxDao.upsert(
                match<OutboxEntity> {
                    it.operationType == "SEND_ATTACHMENT_MESSAGE" &&
                        it.conversationId == "conv-1" &&
                        it.state == OutboxState.PENDING.name
                },
            )
        }
        coVerify { outboxSyncScheduler.scheduleNow() }
    }

    @Test
    fun `sendAttachment classifies a non-image mime type as kind=file`() = runTest {
        coEvery { attachmentCache.copyToCache(any(), any()) } returns "/data/app/cache/fake-copy.pdf"
        every { attachmentCache.sizeOf(any()) } returns 999L

        repository.sendAttachment(
            conversationId = "conv-1",
            contentUri = "content://media/external/files/7",
            mimeType = "application/pdf",
            originalFilename = "report.pdf",
            caption = null,
        )

        coVerify {
            messageDao.upsert(match<MessageEntity> { it.kind == "file" })
        }
    }

    @Test
    fun `sendAttachment classifies an audio mime type as kind=audio and stores the reported duration`() = runTest {
        coEvery { attachmentCache.copyToCache(any(), any()) } returns "/data/app/cache/fake-copy.m4a"
        every { attachmentCache.sizeOf(any()) } returns 5000L

        repository.sendAttachment(
            conversationId = "conv-1",
            contentUri = "file:///data/app/voice_recordings/abc.m4a",
            mimeType = "audio/mp4",
            originalFilename = "voice-message.m4a",
            caption = null,
            durationMs = 4200L,
        )

        coVerify {
            messageDao.upsert(
                match<MessageEntity> {
                    it.kind == "audio" &&
                        it.attachmentDurationMs == 4200L &&
                        it.attachmentId == null // not yet known — assigned once upload succeeds
                },
            )
        }
        coVerify {
            outboxDao.upsert(match<OutboxEntity> { it.operationType == "SEND_ATTACHMENT_MESSAGE" })
        }
    }

    @Test
    fun `sendAttachment classifies a video mime type as kind=video and stores client-extracted duration and dimensions`() = runTest {
        coEvery { attachmentCache.copyToCache(any(), any()) } returns "/data/app/cache/fake-copy.mp4"
        every { attachmentCache.sizeOf(any()) } returns 2_000_000L

        repository.sendAttachment(
            conversationId = "conv-1",
            contentUri = "content://media/external/video/9",
            mimeType = "video/mp4",
            originalFilename = "clip.mp4",
            caption = null,
            durationMs = 8000L,
            thumbnailLocalUri = "/data/app/cache/fake-frame.jpg",
            widthPx = 1920,
            heightPx = 1080,
        )

        coVerify {
            messageDao.upsert(
                match<MessageEntity> {
                    it.kind == "video" &&
                        it.attachmentDurationMs == 8000L &&
                        it.attachmentWidthPx == 1920 &&
                        it.attachmentHeightPx == 1080 &&
                        it.attachmentHasThumbnail == true &&
                        it.localThumbnailUri == "/data/app/cache/fake-frame.jpg"
                },
            )
        }
    }

    @Test
    fun `sendMessage writes a PENDING message locally before any network call, then enqueues a durable outbox entry`() = runTest {
        repository.sendMessage("conv-1", "  hello  ")

        coVerify {
            messageDao.upsert(
                match<MessageEntity> {
                    it.conversationId == "conv-1" &&
                        it.senderId == selfUserId &&
                        it.body == "  hello  " && // sendMessage itself doesn't trim — that's SendMessageUseCase's job
                        it.state == "PENDING" &&
                        it.sequence == null
                },
            )
        }
        coVerify {
            outboxDao.upsert(
                match<OutboxEntity> {
                    it.operationType == "SEND_MESSAGE" &&
                        it.conversationId == "conv-1" &&
                        it.state == OutboxState.PENDING.name &&
                        it.retryCount == 0
                },
            )
        }
        coVerify(exactly = 0) { chatApi.sendMessage(any(), any()) } // never called directly — only the Outbox worker calls the network
        coVerify { outboxSyncScheduler.scheduleNow() }
    }

    @Test
    fun `markRead updates the local read cursor even if the server call fails`() = runTest {
        coEvery { chatApi.markRead(any(), any()) } throws IOException("offline")

        repository.markRead("conv-1", 42L)

        coVerify { conversationDao.updateLastReadSequence("conv-1", 42L) }
        coVerify { chatApi.markRead("conv-1", MarkReadBody(42L)) }
        // no assertion on an exception propagating — the whole point is that it doesn't
    }

    @Test
    fun `markRead calls the server with the exact sequence passed in`() = runTest {
        coEvery { chatApi.markRead(any(), any()) } returns Unit

        repository.markRead("conv-2", 7L)

        coVerify { chatApi.markRead("conv-2", MarkReadBody(7L)) }
    }
}
