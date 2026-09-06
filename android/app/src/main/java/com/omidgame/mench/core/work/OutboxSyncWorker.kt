package com.omidgame.mench.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.omidgame.mench.core.database.ConversationDao
import com.omidgame.mench.core.database.MessageDao
import com.omidgame.mench.core.database.MessageState
import com.omidgame.mench.core.database.OutboxDao
import com.omidgame.mench.core.database.OutboxEntity
import com.omidgame.mench.core.database.OutboxOperationType
import com.omidgame.mench.core.database.OutboxState
import com.omidgame.mench.core.media.AttachmentCache
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.EditMessageBody
import com.omidgame.mench.core.network.ForwardMessageBody
import com.omidgame.mench.core.network.ReactMessageBody
import com.omidgame.mench.core.network.SendMessageBody
import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant

/**
 * Flushes the durable Outbox (see OutboxEntity). Runs via WorkManager, so
 * it can be retried on a schedule even if the app process isn't running —
 * the actual durability guarantee behind spec section 14 ("survives app
 * restart, process death, device restart"). Note this is explicitly
 * imported as androidx.work.ListenableWorker.Result, not kotlin.Result,
 * which Kotlin would otherwise resolve to by default and silently produce
 * a type that doesn't satisfy CoroutineWorker's contract.
 */
@HiltWorker
class OutboxSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val outboxDao: OutboxDao,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val chatApi: ChatApi,
    private val moshi: Moshi,
    private val attachmentCache: AttachmentCache,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pending = outboxDao.listPending(OutboxState.FAILED.name)
        var anyFailure = false

        for (entry in pending) {
            val succeeded = when (entry.operationType) {
                OutboxOperationType.SEND_MESSAGE -> attemptSendMessage(entry)
                OutboxOperationType.SEND_ATTACHMENT_MESSAGE -> attemptSendAttachmentMessage(entry)
                OutboxOperationType.EDIT_MESSAGE -> attemptEditMessage(entry)
                OutboxOperationType.DELETE_MESSAGE -> attemptDeleteMessage(entry)
                OutboxOperationType.SET_REACTION -> attemptSetReaction(entry)
                OutboxOperationType.CLEAR_REACTION -> attemptClearReaction(entry)
                OutboxOperationType.FORWARD_MESSAGE -> attemptForwardMessage(entry)
                // An operation type this worker version doesn't recognize
                // (e.g. from a future phase's payload) is skipped rather
                // than crashing the whole batch — it's left for a newer
                // app version to handle, not silently dropped or retried
                // forever against code that can't understand it.
                else -> true
            }
            if (!succeeded) anyFailure = true
        }

        return if (anyFailure) Result.retry() else Result.success()
    }

    private suspend fun attemptSendMessage(entry: OutboxEntity): Boolean {
        outboxDao.markAttempt(entry.operationId, OutboxState.IN_FLIGHT.name, System.currentTimeMillis())

        val payload = runCatching {
            moshi.adapter(SendMessagePayload::class.java).fromJson(entry.payloadJson)
        }.getOrNull()

        if (payload == null) {
            // Malformed payload can never succeed no matter how many times
            // it's retried — drop it rather than retrying forever, and
            // leave the message row's state as-is (FAILED-looking to the
            // user) rather than pretending success.
            outboxDao.delete(entry.operationId)
            return true
        }

        return try {
            val response = chatApi.sendMessage(
                payload.conversationId,
                SendMessageBody(payload.clientMsgId, payload.body),
            )
            val createdAtEpochMillis = Instant.parse(response.createdAt).toEpochMilli()
            messageDao.markSent(
                clientMsgId = payload.clientMsgId,
                serverId = response.id,
                sequence = response.sequence,
                createdAtEpochMillis = createdAtEpochMillis,
                state = MessageState.SENT.name,
            )
            // The server only broadcasts message.created to OTHER
            // conversation members (see chat.gateway.ts / listOtherMemberIds)
            // — the sender never gets an echo back over the socket. Without
            // this, the sender's own conversation list ordering (by
            // lastMessageAtEpochMillis) would silently go stale after every
            // message they send until the next full refreshConversations().
            conversationDao.updateLastMessageAt(payload.conversationId, createdAtEpochMillis)
            outboxDao.delete(entry.operationId)
            true
        } catch (e: Exception) {
            outboxDao.markAttempt(entry.operationId, OutboxState.PENDING.name, System.currentTimeMillis())
            false
        }
    }

    private suspend fun attemptSendAttachmentMessage(entry: OutboxEntity): Boolean {
        outboxDao.markAttempt(entry.operationId, OutboxState.IN_FLIGHT.name, System.currentTimeMillis())

        val payload = runCatching {
            moshi.adapter(SendAttachmentMessagePayload::class.java).fromJson(entry.payloadJson)
        }.getOrNull()

        if (payload == null) {
            outboxDao.delete(entry.operationId)
            return true
        }

        val localFile = File(payload.localContentUri)
        if (!localFile.exists()) {
            // The cached copy is gone (e.g. app storage was cleared
            // externally) — retrying can never succeed. Drop the outbox
            // entry rather than retrying forever; the message stays
            // visible locally in whatever state it was last in, which is
            // an honest reflection of what actually happened, not a
            // silently-vanishing message.
            outboxDao.delete(entry.operationId)
            return true
        }

        return try {
            // Two network calls in sequence: upload, then send-with-
            // attachmentId. If the process dies or the network drops
            // between them, this whole operation retries from the start —
            // uploadAttachment on the backend has no idempotency key
            // (each retry creates a new attachment row), a known Phase 3a
            // limitation documented in docs/ARCHITECTURE.md rather than
            // silently assumed away. sendMessage itself IS idempotent via
            // clientMsgId, so a retried send after a successful upload
            // does not duplicate the message.
            val mediaType = payload.mimeType.toMediaTypeOrNull()
            val requestBody = localFile.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", payload.originalFilename, requestBody)
            val durationPart = payload.durationMs?.toString()
                ?.toRequestBody("text/plain".toMediaTypeOrNull())
            val widthPart = payload.widthPx?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val heightPart = payload.heightPx?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

            // Only present for video (see VideoMetadataExtractor) — a
            // missing thumbnail file at this point (e.g. extraction failed
            // on-device, or the cache entry is gone) is not fatal, the
            // upload just proceeds without one.
            val thumbnailFile = payload.thumbnailLocalPath?.let { File(it) }?.takeIf { it.exists() }
            val thumbnailPart = thumbnailFile?.let {
                MultipartBody.Part.createFormData(
                    "thumbnail",
                    it.name,
                    it.asRequestBody("image/jpeg".toMediaTypeOrNull()),
                )
            }

            val attachmentResponse = chatApi.uploadAttachment(
                payload.conversationId,
                part,
                durationPart,
                thumbnailPart,
                widthPart,
                heightPart,
            )

            val messageResponse = chatApi.sendMessage(
                payload.conversationId,
                SendMessageBody(payload.clientMsgId, payload.caption, attachmentResponse.id),
            )

            val createdAtEpochMillis = Instant.parse(messageResponse.createdAt).toEpochMilli()
            messageDao.markSent(
                clientMsgId = payload.clientMsgId,
                serverId = messageResponse.id,
                sequence = messageResponse.sequence,
                createdAtEpochMillis = createdAtEpochMillis,
                state = MessageState.SENT.name,
                attachmentId = attachmentResponse.id,
                widthPx = attachmentResponse.widthPx,
                heightPx = attachmentResponse.heightPx,
            )
            conversationDao.updateLastMessageAt(payload.conversationId, createdAtEpochMillis)
            outboxDao.delete(entry.operationId)
            // Free the on-device cached copies now that the server has its
            // own durable copy — without this, every sent attachment
            // would sit in app-private storage forever.
            attachmentCache.delete(payload.localContentUri)
            payload.thumbnailLocalPath?.let { attachmentCache.delete(it) }
            true
        } catch (e: Exception) {
            outboxDao.markAttempt(entry.operationId, OutboxState.PENDING.name, System.currentTimeMillis())
            false
        }
    }

    // Phase 4: edit / delete / react / unreact are all the same shape —
    // the local Room row was already updated optimistically by
    // ChatRepositoryImpl at queue time, so success here is just "delete
    // the outbox entry", with no further Room write. A malformed payload
    // is treated the same way attemptSendMessage treats one: it can never
    // succeed, so it's dropped rather than retried forever.

    private suspend fun attemptEditMessage(entry: OutboxEntity): Boolean {
        outboxDao.markAttempt(entry.operationId, OutboxState.IN_FLIGHT.name, System.currentTimeMillis())
        val payload = runCatching { moshi.adapter(EditMessagePayload::class.java).fromJson(entry.payloadJson) }.getOrNull()
        if (payload == null) {
            outboxDao.delete(entry.operationId)
            return true
        }
        return try {
            chatApi.editMessage(payload.conversationId, payload.serverId, EditMessageBody(payload.body))
            outboxDao.delete(entry.operationId)
            true
        } catch (e: Exception) {
            outboxDao.markAttempt(entry.operationId, OutboxState.PENDING.name, System.currentTimeMillis())
            false
        }
    }

    private suspend fun attemptDeleteMessage(entry: OutboxEntity): Boolean {
        outboxDao.markAttempt(entry.operationId, OutboxState.IN_FLIGHT.name, System.currentTimeMillis())
        val payload = runCatching { moshi.adapter(DeleteMessagePayload::class.java).fromJson(entry.payloadJson) }.getOrNull()
        if (payload == null) {
            outboxDao.delete(entry.operationId)
            return true
        }
        return try {
            chatApi.deleteMessage(payload.conversationId, payload.serverId)
            outboxDao.delete(entry.operationId)
            true
        } catch (e: Exception) {
            outboxDao.markAttempt(entry.operationId, OutboxState.PENDING.name, System.currentTimeMillis())
            false
        }
    }

    private suspend fun attemptSetReaction(entry: OutboxEntity): Boolean {
        outboxDao.markAttempt(entry.operationId, OutboxState.IN_FLIGHT.name, System.currentTimeMillis())
        val payload = runCatching { moshi.adapter(SetReactionPayload::class.java).fromJson(entry.payloadJson) }.getOrNull()
        if (payload == null) {
            outboxDao.delete(entry.operationId)
            return true
        }
        return try {
            chatApi.setReaction(payload.conversationId, payload.serverId, ReactMessageBody(payload.emoji))
            outboxDao.delete(entry.operationId)
            true
        } catch (e: Exception) {
            outboxDao.markAttempt(entry.operationId, OutboxState.PENDING.name, System.currentTimeMillis())
            false
        }
    }

    private suspend fun attemptClearReaction(entry: OutboxEntity): Boolean {
        outboxDao.markAttempt(entry.operationId, OutboxState.IN_FLIGHT.name, System.currentTimeMillis())
        val payload = runCatching { moshi.adapter(ClearReactionPayload::class.java).fromJson(entry.payloadJson) }.getOrNull()
        if (payload == null) {
            outboxDao.delete(entry.operationId)
            return true
        }
        return try {
            chatApi.clearReaction(payload.conversationId, payload.serverId)
            outboxDao.delete(entry.operationId)
            true
        } catch (e: Exception) {
            outboxDao.markAttempt(entry.operationId, OutboxState.PENDING.name, System.currentTimeMillis())
            false
        }
    }

    /**
     * Unlike the four operations above, forward DOES need a Room update on
     * success — it created a brand-new PENDING message locally (see
     * ChatRepositoryImpl.forwardMessage), the same optimistic-send shape as
     * attemptSendMessage, just against the forward endpoint instead of the
     * plain send endpoint.
     */
    private suspend fun attemptForwardMessage(entry: OutboxEntity): Boolean {
        outboxDao.markAttempt(entry.operationId, OutboxState.IN_FLIGHT.name, System.currentTimeMillis())
        val payload = runCatching { moshi.adapter(ForwardMessagePayload::class.java).fromJson(entry.payloadJson) }.getOrNull()
        if (payload == null) {
            outboxDao.delete(entry.operationId)
            return true
        }
        return try {
            val response = chatApi.forwardMessage(
                payload.targetConversationId,
                ForwardMessageBody(payload.sourceServerId, payload.clientMsgId),
            )
            val createdAtEpochMillis = Instant.parse(response.createdAt).toEpochMilli()
            messageDao.markSent(
                clientMsgId = payload.clientMsgId,
                serverId = response.id,
                sequence = response.sequence,
                createdAtEpochMillis = createdAtEpochMillis,
                state = MessageState.SENT.name,
            )
            conversationDao.updateLastMessageAt(payload.targetConversationId, createdAtEpochMillis)
            outboxDao.delete(entry.operationId)
            true
        } catch (e: Exception) {
            outboxDao.markAttempt(entry.operationId, OutboxState.PENDING.name, System.currentTimeMillis())
            false
        }
    }
}
