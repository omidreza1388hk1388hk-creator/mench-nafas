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
import com.omidgame.mench.core.media.ProgressRequestBody
import com.omidgame.mench.core.media.UploadCancelledException
import com.omidgame.mench.core.media.UploadProgress
import retrofit2.HttpException
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

    /**
     * Phase 1B retry policy. A failure is "permanent" for client errors the server will keep
     * rejecting (4xx except timeout / too-many-requests), and after [MAX_ATTEMPTS]
     * tries an entry is parked as FAILED instead of retrying forever. Parked message sends flip
     * the bubble to FAILED so the user can tap to retry or long-press to delete.
     * Returns true when the entry is finished (do NOT ask WorkManager to retry it).
     */
    private suspend fun recordFailure(entry: OutboxEntity, error: Exception, clientMsgId: String?): Boolean {
        val cancelled = error is UploadCancelledException ||
            (clientMsgId != null && UploadProgress.isCancelled(clientMsgId))
        val permanent = error is HttpException &&
            error.code() in 400..499 && error.code() !in RETRYABLE_CLIENT_CODES
        val exhausted = entry.retryCount + 1 >= MAX_ATTEMPTS
        if (cancelled) {
            clientMsgId?.let {
                UploadProgress.acknowledgeCancel(it)
                UploadProgress.clear(it)
            }
            outboxDao.setState(entry.operationId, OutboxState.FAILED.name)
            clientMsgId?.let { messageDao.updateState(it, MessageState.FAILED.name) }
            return true
        }
        if (permanent || exhausted) {
            if (clientMsgId != null) {
                UploadProgress.clear(clientMsgId)
                outboxDao.setState(entry.operationId, OutboxState.FAILED.name)
                messageDao.updateState(clientMsgId, MessageState.FAILED.name)
            } else {
                // Edit/delete/reaction: the local row already shows the optimistic result;
                // there is no bubble to mark, so just stop retrying.
                outboxDao.delete(entry.operationId)
            }
            return true
        }
        outboxDao.markAttempt(entry.operationId, OutboxState.PENDING.name, System.currentTimeMillis())
        return false
    }

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

        // Phase 4C: in a secure chat the wire carries ciphertext; with no partner key we retry later instead of sending plaintext.
        val wireBody = com.omidgame.mench.core.crypto.E2eeManager.prepareOutgoing(chatApi, payload.conversationId, payload.body)
            ?: return false
        return try {
            val response = chatApi.sendMessage(
                payload.conversationId,
                SendMessageBody(payload.clientMsgId, wireBody, replyToMessageId = payload.replyToMessageId, silent = payload.silent.takeIf { it }),
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
            recordFailure(entry, e, payload.clientMsgId)
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
            // Phase 1B leftover: big videos are transcoded (H.264/AAC, longer edge <= 1280) before upload.
            // The progress ring fills during this step; if it fails or is not smaller, the original is uploaded.
            var uploadFile = localFile
            var uploadMime = payload.mimeType
            var uploadName = payload.originalFilename
            if (payload.mimeType.startsWith("video/") &&
                com.omidgame.mench.core.media.VideoTranscoder.needsTranscode(payload.widthPx, payload.heightPx)
            ) {
                val transcoded = File(applicationContext.cacheDir, "transcoded_${payload.clientMsgId}.mp4")
                UploadProgress.report(payload.clientMsgId, 0f)
                val ok = com.omidgame.mench.core.media.VideoTranscoder.transcode(
                    applicationContext, localFile, transcoded, payload.widthPx!!, payload.heightPx!!,
                ) { fraction -> UploadProgress.report(payload.clientMsgId, fraction) }
                if (UploadProgress.isCancelled(payload.clientMsgId)) {
                    transcoded.delete()
                    throw UploadCancelledException()
                }
                if (ok && transcoded.exists() && transcoded.length() in 1 until localFile.length()) {
                    uploadFile = transcoded
                    uploadMime = "video/mp4"
                    uploadName = payload.originalFilename.substringBeforeLast('.', payload.originalFilename) + ".mp4"
                } else {
                    transcoded.delete()
                }
            }
            val mediaType = uploadMime.toMediaTypeOrNull()
            val requestBody = ProgressRequestBody(uploadFile.asRequestBody(mediaType), payload.clientMsgId)
            UploadProgress.report(payload.clientMsgId, 0f)
            if (UploadProgress.isCancelled(payload.clientMsgId)) throw UploadCancelledException()
            val part = MultipartBody.Part.createFormData("file", uploadName, requestBody)
            val durationPart = payload.durationMs?.toString()
                ?.toRequestBody("text/plain".toMediaTypeOrNull())
            val widthPart = payload.widthPx?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val heightPart = payload.heightPx?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val waveformPart = payload.waveform?.toRequestBody("text/plain".toMediaTypeOrNull())

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

            val attachmentResponse = if (uploadFile.length() >= com.omidgame.mench.core.media.ChunkedUploader.THRESHOLD_BYTES) {
                // Big file: resumable chunks, so a dropped connection continues instead of restarting.
                try {
                    com.omidgame.mench.core.media.ChunkedUploader(applicationContext, chatApi).upload(
                        clientMsgId = payload.clientMsgId,
                        conversationId = payload.conversationId,
                        file = uploadFile,
                        mimeType = uploadMime,
                        filename = uploadName,
                        durationMs = payload.durationMs,
                        widthPx = payload.widthPx,
                        heightPx = payload.heightPx,
                        thumbnail = thumbnailFile,
                    )
                } catch (e: com.omidgame.mench.core.media.ChunkedUploadCancelled) {
                    throw UploadCancelledException()
                }
            } else {
                chatApi.uploadAttachment(
                    payload.conversationId,
                    part,
                    durationPart,
                    thumbnailPart,
                    widthPart,
                    heightPart,
                    waveformPart,
                )
            }

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
            UploadProgress.clear(payload.clientMsgId)
            // Free the on-device cached copies now that the server has its
            // own durable copy — without this, every sent attachment
            // would sit in app-private storage forever.
            attachmentCache.delete(payload.localContentUri)
            payload.thumbnailLocalPath?.let { attachmentCache.delete(it) }
            true
        } catch (e: Exception) {
            recordFailure(entry, e, payload.clientMsgId)
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
        val wireBody = com.omidgame.mench.core.crypto.E2eeManager.prepareOutgoing(chatApi, payload.conversationId, payload.body)
            ?: return false
        return try {
            chatApi.editMessage(payload.conversationId, payload.serverId, EditMessageBody(wireBody))
            outboxDao.delete(entry.operationId)
            true
        } catch (e: Exception) {
            recordFailure(entry, e, null)
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
            recordFailure(entry, e, null)
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
            recordFailure(entry, e, null)
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
            recordFailure(entry, e, null)
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
            recordFailure(entry, e, payload.clientMsgId)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
        val RETRYABLE_CLIENT_CODES = setOf(408, 425, 429)
    }
}
