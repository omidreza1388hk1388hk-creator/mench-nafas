package com.omidgame.mench.feature.chat.data

import com.google.common.truth.Truth.assertThat
import com.omidgame.mench.core.database.MessageEntity
import com.omidgame.mench.core.database.MessageState
import com.omidgame.mench.core.network.ConversationResponse
import com.omidgame.mench.core.network.MessageAttachmentSummaryResponse
import com.omidgame.mench.core.network.MessageResponse
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import org.junit.Test

private fun textMessageEntity(
    clientMsgId: String = "client-1",
    senderId: String = "user-1",
    state: String = MessageState.PENDING.name,
) = MessageEntity(
    clientMsgId = clientMsgId,
    conversationId = "conv-1",
    senderId = senderId,
    serverId = null,
    kind = "text",
    body = "hi",
    sequence = null,
    createdAtEpochMillis = 0L,
    state = state,
    attachmentId = null,
    attachmentMimeType = null,
    attachmentOriginalFilename = null,
    attachmentSizeBytes = null,
    attachmentWidthPx = null,
    attachmentHeightPx = null,
    attachmentHasThumbnail = false,
    localContentUri = null,
    localThumbnailUri = null,
)

class ChatMappersTest {

    @Test
    fun `ConversationResponse toEntity preserves the explicitly passed lastReadSequence, not zero`() {
        val response = ConversationResponse(
            id = "conv-1",
            kind = "direct",
            title = null,
            createdBy = "user-1",
            createdAt = "2026-01-01T00:00:00.000Z",
            lastMessageAt = "2026-01-02T00:00:00.000Z",
            otherUserId = "user-2",
            otherUserPhoneE164 = "+15559876543",
            otherUserDisplayName = "Bee",
        )

        val entity = response.toEntity(existingLastReadSequence = 42)

        assertThat(entity.lastReadSequence).isEqualTo(42)
        assertThat(entity.lastMessageAtEpochMillis).isNotNull()
        assertThat(entity.otherUserId).isEqualTo("user-2")
        assertThat(entity.otherUserDisplayName).isEqualTo("Bee")
    }

    @Test
    fun `ConversationResponse toEntity handles a null lastMessageAt (brand new conversation)`() {
        val response = ConversationResponse(
            id = "conv-2",
            kind = "direct",
            title = null,
            createdBy = "user-1",
            createdAt = "2026-01-01T00:00:00.000Z",
            lastMessageAt = null,
            otherUserId = "user-2",
            otherUserPhoneE164 = "+15559876543",
            otherUserDisplayName = null,
        )

        val entity = response.toEntity(existingLastReadSequence = 0)

        assertThat(entity.lastMessageAtEpochMillis).isNull()
    }

    @Test
    fun `MessageResponse toEntity always maps to SENT state — it only exists server-side once persisted`() {
        val response = MessageResponse(
            id = "msg-1",
            conversationId = "conv-1",
            senderId = "user-1",
            clientMsgId = "client-1",
            kind = "text",
            body = "hi",
            attachment = null,
            sequence = 7,
            createdAt = "2026-01-01T00:00:00.000Z",
            editedAt = null,
            deletedAt = null,
            forwardedFromMessageId = null,
        )

        val entity = response.toEntity(existingLocalContentUri = null, existingLocalThumbnailUri = null)

        assertThat(entity.state).isEqualTo(MessageState.SENT.name)
        assertThat(entity.sequence).isEqualTo(7)
        assertThat(entity.serverId).isEqualTo("msg-1")
    }

    @Test
    fun `MessageResponse toEntity preserves an existing local content uri rather than clobbering it to null`() {
        val response = MessageResponse(
            id = "msg-1",
            conversationId = "conv-1",
            senderId = "user-1",
            clientMsgId = "client-1",
            kind = "image",
            body = null,
            attachment = MessageAttachmentSummaryResponse(
                id = "att-1",
                kind = "image",
                mimeType = "image/jpeg",
                originalFilename = "photo.jpg",
                sizeBytes = 1000L,
                widthPx = 800,
                heightPx = 600,
                durationMs = null,
                hasThumbnail = true,
            ),
            sequence = 7,
            createdAt = "2026-01-01T00:00:00.000Z",
            editedAt = null,
            deletedAt = null,
            forwardedFromMessageId = null,
        )

        val entity = response.toEntity(
            existingLocalContentUri = "/data/app/cache/already-here.jpg",
            existingLocalThumbnailUri = null,
        )

        assertThat(entity.localContentUri).isEqualTo("/data/app/cache/already-here.jpg")
        assertThat(entity.attachmentHasThumbnail).isTrue()
        assertThat(entity.attachmentWidthPx).isEqualTo(800)
    }

    @Test
    fun `MessageEntity toDomain marks isOwn correctly based on the current self user id`() {
        val entity = textMessageEntity(senderId = "user-self")

        assertThat(entity.toDomain(selfUserId = "user-self").isOwn).isTrue()
        assertThat(entity.toDomain(selfUserId = "user-other").isOwn).isFalse()
        assertThat(entity.toDomain(selfUserId = null).isOwn).isFalse()
    }

    @Test
    fun `MessageEntity toDomain falls back to PENDING for an unrecognized state string rather than crashing`() {
        val entity = textMessageEntity(state = "SOME_FUTURE_STATE_THIS_APP_VERSION_DOESNT_KNOW")

        val domain = entity.toDomain(selfUserId = "user-1")

        assertThat(domain.state.name).isEqualTo("PENDING")
    }

    @Test
    fun `MessageEntity toDomain has no attachment for a plain text message`() {
        val entity = textMessageEntity()

        assertThat(entity.toDomain(selfUserId = "user-1").attachment).isNull()
        assertThat(entity.toDomain(selfUserId = "user-1").kind).isEqualTo(DomainMessageKind.TEXT)
    }

    @Test
    fun `MessageEntity toDomain reports hasThumbnail from the stored column, never derived from attachmentId alone`() {
        val fileEntity = textMessageEntity().copy(
            kind = "file",
            attachmentId = "att-1",
            attachmentMimeType = "application/pdf",
            attachmentOriginalFilename = "report.pdf",
            attachmentSizeBytes = 5000L,
            attachmentHasThumbnail = false, // a PDF never has one, even though attachmentId is set
        )

        val domain = fileEntity.toDomain(selfUserId = "user-1")

        assertThat(domain.kind).isEqualTo(DomainMessageKind.FILE)
        assertThat(domain.attachment?.hasThumbnail).isFalse()
    }

    @Test
    fun `ConversationResponse toEntity carries the group title through untouched`() {
        val response = ConversationResponse(
            id = "conv-3",
            kind = "group",
            title = "Trip planning",
            createdBy = "user-1",
            createdAt = "2026-01-01T00:00:00.000Z",
            lastMessageAt = null,
            otherUserId = null,
            otherUserPhoneE164 = null,
            otherUserDisplayName = null,
        )

        val entity = response.toEntity(existingLastReadSequence = 0)

        assertThat(entity.kind).isEqualTo("group")
        assertThat(entity.title).isEqualTo("Trip planning")
        assertThat(entity.toDomain().isGroup).isTrue()
    }

    @Test
    fun `MessageResponse toEntity round-trips reactions through the local JSON codec, with reactedByMe derived not stored`() {
        val response = MessageResponse(
            id = "msg-2",
            conversationId = "conv-1",
            senderId = "user-2",
            clientMsgId = "client-2",
            kind = "text",
            body = "hey",
            attachment = null,
            sequence = 8,
            createdAt = "2026-01-01T00:00:00.000Z",
            editedAt = "2026-01-01T00:05:00.000Z",
            deletedAt = null,
            forwardedFromMessageId = null,
            reactions = listOf(
                com.omidgame.mench.core.network.ReactionSummaryResponse(emoji = "\uD83D\uDC4D", userIds = listOf("user-1", "user-2")),
            ),
        )

        val entity = response.toEntity(existingLocalContentUri = null, existingLocalThumbnailUri = null)
        assertThat(entity.editedAtEpochMillis).isNotNull()
        assertThat(entity.reactionsJson).isNotNull()

        val asSelfUser1 = entity.toDomain(selfUserId = "user-1").reactions.single()
        assertThat(asSelfUser1.reactedByMe).isTrue()

        val asSelfUser3 = entity.toDomain(selfUserId = "user-3").reactions.single()
        assertThat(asSelfUser3.reactedByMe).isFalse()
    }

    @Test
    fun `MessageEntity toDomain surfaces a deleted message's deletedAtEpochMillis rather than dropping the row`() {
        val entity = textMessageEntity().copy(deletedAtEpochMillis = 1234L)

        val domain = entity.toDomain(selfUserId = "user-1")

        assertThat(domain.deletedAtEpochMillis).isEqualTo(1234L)
    }
}
