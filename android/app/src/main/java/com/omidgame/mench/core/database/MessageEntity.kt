package com.omidgame.mench.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * clientMsgId (a UUID generated on-device before the first send attempt)
 * is the local primary key rather than the server's own message id. This
 * is what makes optimistic local-first rendering possible: the row is
 * created and shown in the UI the instant the user hits send, before any
 * network round trip, and is later reconciled in place (serverId/sequence
 * filled in, state advanced) once the server responds — never replaced or
 * duplicated. It also lines up with the server's own dedupe key
 * (conversation_id, sender_id, client_msg_id), so a retried send from the
 * Outbox is naturally idempotent end to end.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val clientMsgId: String,
    val conversationId: String,
    val senderId: String,
    val serverId: String?,
    val kind: String, // "text" | "image" | "file" | "audio" — mirrors messages.kind server-side
    val body: String?,
    val sequence: Long?,
    val createdAtEpochMillis: Long,
    val state: String, // MessageState enum name — see MessageState.kt

    // Attachment fields — all null for a plain text message. A message
    // carries at most one attachment (Phase 3a scope), matching the
    // server's messages.attachment_id column.
    val attachmentId: String?,
    val attachmentMimeType: String?,
    val attachmentOriginalFilename: String?,
    val attachmentSizeBytes: Long?,
    val attachmentWidthPx: Int?,
    val attachmentHeightPx: Int?,
    /** Only meaningful for kind="audio" — the recording length, reported by whichever device recorded it. */
    val attachmentDurationMs: Long?,
    /** Server-reported truth, not derived locally — a non-image file (e.g. a PDF) never has one, and deriving "has thumbnail" from "has an attachmentId" would get that wrong. */
    val attachmentHasThumbnail: Boolean,
    /** Set once the attachment bytes are cached on-device (upload source or downloaded copy) — see AttachmentCache. */
    val localContentUri: String?,
    val localThumbnailUri: String?,

    // Phase 4: edit / delete / forward / reactions. All default to null —
    // a freshly-composed outgoing message (sendMessage/sendAttachment)
    // has none of these yet, so existing call sites that build a
    // MessageEntity for a brand-new send don't need to change.
    val editedAtEpochMillis: Long? = null,
    /** Non-null means this message was deleted — the row is kept (so history has no gap, matching the server's messages.deleted_at behavior) but body/attachment are no longer rendered. */
    val deletedAtEpochMillis: Long? = null,
    val forwardedFromMessageId: String? = null,
    /**
     * Moshi-serialized `List<ReactionSummary>`, not a normalized child
     * table — reactions always arrive already-aggregated from the server
     * (see MessagesRepository.listReactionsForMessage), so there is no
     * local write pattern that needs per-row reaction inserts the way
     * there is for, say, messages themselves. Flattening this onto
     * MessageEntity mirrors how attachment fields are already flattened
     * here rather than normalized.
     */
    val reactionsJson: String? = null,
)
