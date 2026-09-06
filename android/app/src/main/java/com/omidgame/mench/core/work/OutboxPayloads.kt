package com.omidgame.mench.core.work

import com.squareup.moshi.JsonClass

/** Serialized into OutboxEntity.payloadJson for operationType == SEND_MESSAGE. */
@JsonClass(generateAdapter = true)
data class SendMessagePayload(
    val conversationId: String,
    val clientMsgId: String,
    val body: String,
)

/**
 * Serialized into OutboxEntity.payloadJson for operationType ==
 * SEND_ATTACHMENT_MESSAGE. localContentUri points into AttachmentCache's
 * app-private storage (not the original picker Uri — see AttachmentCache
 * for why), so it's still readable no matter how long this entry sits
 * pending. Same reasoning applies to thumbnailLocalPath (VideoMetadataExtractor
 * already wrote it into app-private storage at send time).
 */
@JsonClass(generateAdapter = true)
data class SendAttachmentMessagePayload(
    val conversationId: String,
    val clientMsgId: String,
    val localContentUri: String,
    val mimeType: String,
    val originalFilename: String,
    val caption: String?,
    /** Required by the backend for kind=audio and kind=video uploads (see AttachmentsService). */
    val durationMs: Long?,
    /** Video only. */
    val thumbnailLocalPath: String?,
    val widthPx: Int?,
    val heightPx: Int?,
)

// Phase 4 (edit/delete/forward/reactions) payloads. Unlike SEND_MESSAGE /
// SEND_ATTACHMENT_MESSAGE, these never need a Room write on success — the
// local row was already updated optimistically at the moment the
// operation was queued (see ChatRepositoryImpl), so OutboxSyncWorker's
// job for all four is just "call the API, then delete the outbox entry".

@JsonClass(generateAdapter = true)
data class EditMessagePayload(val conversationId: String, val serverId: String, val body: String)

@JsonClass(generateAdapter = true)
data class DeleteMessagePayload(val conversationId: String, val serverId: String)

@JsonClass(generateAdapter = true)
data class SetReactionPayload(val conversationId: String, val serverId: String, val emoji: String)

@JsonClass(generateAdapter = true)
data class ClearReactionPayload(val conversationId: String, val serverId: String)

/** targetConversationId is where the new forwarded copy lands; sourceServerId is the original message being forwarded (looked up server-side — see MessagesController.forwardMessage). */
@JsonClass(generateAdapter = true)
data class ForwardMessagePayload(
    val targetConversationId: String,
    val sourceServerId: String,
    val clientMsgId: String,
)
