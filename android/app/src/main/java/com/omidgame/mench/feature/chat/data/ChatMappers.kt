package com.omidgame.mench.feature.chat.data

import com.omidgame.mench.core.database.ConversationEntity
import com.omidgame.mench.core.database.MessageEntity
import com.omidgame.mench.core.database.MessageState
import com.omidgame.mench.core.network.ConversationMemberResponse
import com.omidgame.mench.core.network.ConversationResponse
import com.omidgame.mench.core.network.MessageResponse
import com.omidgame.mench.core.network.ReactionSummaryResponse
import com.omidgame.mench.core.network.realtime.MessageWire
import com.omidgame.mench.core.network.realtime.ConversationWire
import com.omidgame.mench.core.network.realtime.ReactionSummaryWire
import com.omidgame.mench.feature.chat.domain.Conversation
import com.omidgame.mench.feature.chat.domain.ConversationMember
import com.omidgame.mench.feature.chat.domain.DomainMessageKind
import com.omidgame.mench.feature.chat.domain.DomainMessageState
import com.omidgame.mench.feature.chat.domain.Message
import com.omidgame.mench.feature.chat.domain.MessageAttachment
import com.omidgame.mench.feature.chat.domain.ReactionSummary
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.time.Instant

/**
 * emoji + userIds only, no reactedByMe — this is purely the on-disk cache
 * format for MessageEntity.reactionsJson (see that field's doc comment
 * for why it's a flattened column rather than a Room child table).
 * reactedByMe is derived at MessageEntity.toDomain() time against
 * whichever user is "self" right now, never stored, so it can't go stale
 * if the signed-in user ever changed.
 */
@JsonClass(generateAdapter = true)
internal data class ReactionSummaryLocal(val emoji: String, val userIds: List<String>)

/**
 * A tiny standalone Moshi instance rather than reusing the app's DI-provided
 * one — this is purely an internal cache-encoding detail of
 * MessageEntity.reactionsJson, not a wire format, and these mapper
 * functions (toDomain in particular, called from a plain Flow.map) have
 * no Hilt injection point to receive one through.
 */
internal val reactionsListAdapter = Moshi.Builder().build()
    .adapter<List<ReactionSummaryLocal>>(Types.newParameterizedType(List::class.java, ReactionSummaryLocal::class.java))

internal fun List<ReactionSummaryResponse>.toReactionsJson(): String? =
    if (isEmpty()) null else reactionsListAdapter.toJson(map { ReactionSummaryLocal(it.emoji, it.userIds) })

internal fun List<ReactionSummaryWire>.toReactionsJsonWire(): String? =
    if (isEmpty()) null else reactionsListAdapter.toJson(map { ReactionSummaryLocal(it.emoji, it.userIds) })

internal fun String?.toReactionSummaries(selfUserId: String?): List<ReactionSummary> {
    if (this == null) return emptyList()
    val local = runCatching { reactionsListAdapter.fromJson(this) }.getOrNull() ?: return emptyList()
    return local.map { ReactionSummary(it.emoji, it.userIds, reactedByMe = selfUserId != null && selfUserId in it.userIds) }
}

/**
 * existingLastReadSequence has no default on purpose: it's tempting to
 * default it to 0, but that would silently reset a conversation's local
 * read cursor back to "unread" every time refreshConversations() re-syncs
 * from the server (whose ConversationDto doesn't carry this field at all —
 * it's purely local, client-side state). Forcing callers to pass it
 * explicitly is what caught that bug while writing this, rather than
 * after shipping it.
 */
internal fun ConversationResponse.toEntity(existingLastReadSequence: Long): ConversationEntity =
    ConversationEntity(
        id = id,
        kind = kind,
        title = title,
        otherUserId = otherUserId,
        otherUserDisplayName = otherUserDisplayName,
        otherUserPhoneE164 = otherUserPhoneE164,
        lastMessageAtEpochMillis = lastMessageAt?.let { Instant.parse(it).toEpochMilli() },
        lastReadSequence = existingLastReadSequence,
    )

/** Phase 6 — the conversation.added realtime event's payload, for a member who was just added to a group. Always a brand-new local row, so there is no existing local read cursor to preserve: 0 is correct here (unlike ConversationResponse.toEntity's refresh path), since this user has never had a read position in a conversation they only just learned exists. */
internal fun ConversationWire.toEntity(): ConversationEntity =
    ConversationEntity(
        id = id,
        kind = kind,
        title = title,
        otherUserId = otherUserId,
        otherUserDisplayName = otherUserDisplayName,
        otherUserPhoneE164 = otherUserPhoneE164,
        lastMessageAtEpochMillis = lastMessageAt?.let { Instant.parse(it).toEpochMilli() },
        lastReadSequence = 0,
    )

internal fun ConversationMemberResponse.toDomain(): ConversationMember =
    ConversationMember(
        userId = userId,
        phoneE164 = phoneE164,
        displayName = displayName,
        role = role,
    )

internal fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    kind = kind,
    title = title,
    otherUserDisplayName = otherUserDisplayName,
    otherUserPhoneE164 = otherUserPhoneE164,
    lastMessageAtEpochMillis = lastMessageAtEpochMillis,
    lastReadSequence = lastReadSequence,
)

/**
 * A message arriving from the server (history fetch or WS) always
 * overwrites local attachment metadata fields but preserves any existing
 * localContentUri/localThumbnailUri — those only ever come from this
 * device's own Outbox/download cache, never from the network, so a
 * refetch must not clobber them back to null.
 */
internal fun MessageResponse.toEntity(existingLocalContentUri: String?, existingLocalThumbnailUri: String?): MessageEntity =
    MessageEntity(
        clientMsgId = clientMsgId,
        conversationId = conversationId,
        senderId = senderId,
        serverId = id,
        kind = kind,
        body = body,
        sequence = sequence,
        createdAtEpochMillis = Instant.parse(createdAt).toEpochMilli(),
        state = MessageState.SENT.name,
        attachmentId = attachment?.id,
        attachmentMimeType = attachment?.mimeType,
        attachmentOriginalFilename = attachment?.originalFilename,
        attachmentSizeBytes = attachment?.sizeBytes,
        attachmentWidthPx = attachment?.widthPx,
        attachmentHeightPx = attachment?.heightPx,
        attachmentDurationMs = attachment?.durationMs,
        attachmentHasThumbnail = attachment?.hasThumbnail ?: false,
        localContentUri = existingLocalContentUri,
        localThumbnailUri = existingLocalThumbnailUri,
        editedAtEpochMillis = editedAt?.let { Instant.parse(it).toEpochMilli() },
        deletedAtEpochMillis = deletedAt?.let { Instant.parse(it).toEpochMilli() },
        forwardedFromMessageId = forwardedFromMessageId,
        reactionsJson = reactions.toReactionsJson(),
    )

internal fun MessageWire.toEntity(existingLocalContentUri: String?, existingLocalThumbnailUri: String?): MessageEntity =
    MessageEntity(
        clientMsgId = clientMsgId,
        conversationId = conversationId,
        senderId = senderId,
        serverId = id,
        kind = kind,
        body = body,
        sequence = sequence,
        createdAtEpochMillis = Instant.parse(createdAt).toEpochMilli(),
        state = MessageState.SENT.name,
        attachmentId = attachment?.id,
        attachmentMimeType = attachment?.mimeType,
        attachmentOriginalFilename = attachment?.originalFilename,
        attachmentSizeBytes = attachment?.sizeBytes,
        attachmentWidthPx = attachment?.widthPx,
        attachmentHeightPx = attachment?.heightPx,
        attachmentDurationMs = attachment?.durationMs,
        attachmentHasThumbnail = attachment?.hasThumbnail ?: false,
        localContentUri = existingLocalContentUri,
        localThumbnailUri = existingLocalThumbnailUri,
        editedAtEpochMillis = editedAt?.let { Instant.parse(it).toEpochMilli() },
        deletedAtEpochMillis = deletedAt?.let { Instant.parse(it).toEpochMilli() },
        forwardedFromMessageId = forwardedFromMessageId,
        reactionsJson = reactions.toReactionsJsonWire(),
    )

internal fun MessageEntity.toDomain(selfUserId: String?): Message = Message(
    clientMsgId = clientMsgId,
    serverId = serverId,
    conversationId = conversationId,
    senderId = senderId,
    kind = runCatching { DomainMessageKind.valueOf(kind.uppercase()) }.getOrDefault(DomainMessageKind.TEXT),
    body = body,
    attachment = if (attachmentMimeType != null) {
        MessageAttachment(
            id = attachmentId,
            mimeType = attachmentMimeType,
            originalFilename = attachmentOriginalFilename ?: "",
            sizeBytes = attachmentSizeBytes ?: 0L,
            widthPx = attachmentWidthPx,
            heightPx = attachmentHeightPx,
            durationMs = attachmentDurationMs,
            hasThumbnail = attachmentHasThumbnail,
            localContentUri = localContentUri,
            localThumbnailUri = localThumbnailUri,
        )
    } else {
        null
    },
    sequence = sequence,
    createdAtEpochMillis = createdAtEpochMillis,
    state = runCatching { DomainMessageState.valueOf(state) }.getOrDefault(DomainMessageState.PENDING),
    isOwn = senderId == selfUserId,
    editedAtEpochMillis = editedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis,
    forwardedFromMessageId = forwardedFromMessageId,
    reactions = reactionsJson.toReactionSummaries(selfUserId),
)
