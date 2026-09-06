package com.omidgame.mench.feature.chat.data

import com.omidgame.mench.core.database.ConversationDao
import com.omidgame.mench.core.database.MessageDao
import com.omidgame.mench.core.database.MessageEntity
import com.omidgame.mench.core.database.MessageState
import com.omidgame.mench.core.database.OutboxDao
import com.omidgame.mench.core.database.OutboxEntity
import com.omidgame.mench.core.database.OutboxOperationType
import com.omidgame.mench.core.database.OutboxState
import com.omidgame.mench.core.database.UserDao
import com.omidgame.mench.core.media.AttachmentCache
import com.omidgame.mench.core.network.AddMembersBody
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.CreateDirectConversationBody
import com.omidgame.mench.core.network.CreateGroupBody
import com.omidgame.mench.core.network.MarkReadBody
import com.omidgame.mench.core.network.RenameGroupBody
import com.omidgame.mench.core.network.realtime.RealtimeClient
import com.omidgame.mench.core.network.realtime.ServerToClientEvent
import com.omidgame.mench.core.security.TokenStore
import com.omidgame.mench.core.work.ClearReactionPayload
import com.omidgame.mench.core.work.DeleteMessagePayload
import com.omidgame.mench.core.work.EditMessagePayload
import com.omidgame.mench.core.work.ForwardMessagePayload
import com.omidgame.mench.core.work.OutboxSyncScheduler
import com.omidgame.mench.core.work.SendAttachmentMessagePayload
import com.omidgame.mench.core.work.SendMessagePayload
import com.omidgame.mench.core.work.SetReactionPayload
import com.omidgame.mench.feature.chat.domain.ChatFailureReason
import com.omidgame.mench.feature.chat.domain.ChatRepository
import com.omidgame.mench.feature.chat.domain.ChatResult
import com.omidgame.mench.feature.chat.domain.Conversation
import com.omidgame.mench.feature.chat.domain.ConversationMember
import com.omidgame.mench.feature.chat.domain.GroupChangeSignal
import com.omidgame.mench.feature.chat.domain.Message
import com.omidgame.mench.feature.chat.domain.ReactionSummary
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val outboxDao: OutboxDao,
    private val userDao: UserDao,
    private val tokenStore: TokenStore,
    private val realtimeClient: RealtimeClient,
    private val outboxSyncScheduler: OutboxSyncScheduler,
    private val moshi: Moshi,
    private val attachmentCache: AttachmentCache,
) : ChatRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventCollectionJob: Job? = null
    private var cachedSelfUserId: String? = null

    private val _typingConversationIds = MutableStateFlow<Set<String>>(emptySet())

    override fun observeTypingConversationIds(): Flow<Set<String>> = _typingConversationIds

    /**
     * replay = 0, extraBufferCapacity = 8: a transient signal for
     * whichever GroupInfoViewModel happens to be collecting right now
     * (same "no persistence" spirit as _typingConversationIds above) —
     * unlike that StateFlow, there's no meaningful "current value" to
     * replay to a late subscriber (a freshly-opened Group Info screen
     * already does its own load() in init{}), so a SharedFlow with no
     * replay is the right shape here, not a StateFlow.
     */
    private val _groupChanges = kotlinx.coroutines.flow.MutableSharedFlow<GroupChangeSignal>(
        replay = 0,
        extraBufferCapacity = 8,
    )

    override fun observeGroupChanges(): Flow<GroupChangeSignal> = _groupChanges

    override suspend fun currentUserId(): String? = selfUserId()

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observeForConversation(conversationId).map { entities ->
            val selfId = selfUserId()
            entities.map { it.toDomain(selfId) }
        }

    override suspend fun startDirectConversation(targetPhoneE164: String): ChatResult<Conversation> =
        withContext(Dispatchers.IO) {
            try {
                val user = chatApi.lookupUserByPhone(targetPhoneE164)
                val response = chatApi.createDirectConversation(CreateDirectConversationBody(user.id))
                val existing = conversationDao.getById(response.id)
                val entity = response.toEntity(existing?.lastReadSequence ?: 0)
                conversationDao.upsert(entity)
                ChatResult.Success(entity.toDomain())
            } catch (e: IOException) {
                ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
            } catch (e: HttpException) {
                when (e.code()) {
                    404 -> ChatResult.Failure(ChatFailureReason.USER_NOT_FOUND)
                    400 -> ChatResult.Failure(ChatFailureReason.CANNOT_MESSAGE_SELF)
                    else -> ChatResult.Failure(ChatFailureReason.UNKNOWN)
                }
            }
        }

    override suspend fun refreshConversations(): ChatResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val remote = chatApi.listConversations()
            val entities = remote.map { response ->
                val existing = conversationDao.getById(response.id)
                response.toEntity(existing?.lastReadSequence ?: 0)
            }
            conversationDao.upsertAll(entities)
            ChatResult.Success(Unit)
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: Exception) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    /**
     * Phone numbers, not user ids — same public shape as
     * startDirectConversation, resolved one lookup at a time via the
     * existing /users/lookup endpoint rather than requiring a bulk
     * resolve-by-phone endpoint that doesn't exist yet. A group with 10
     * invitees means 10 sequential lookups; acceptable for the size of
     * group this UI supports (typed-in phone numbers, no contact-list
     * multi-select), not something worth a new batched endpoint for yet.
     */
    override suspend fun createGroup(title: String, memberPhoneNumbers: List<String>): ChatResult<Conversation> =
        withContext(Dispatchers.IO) {
            try {
                val memberIds = memberPhoneNumbers.map { chatApi.lookupUserByPhone(it).id }
                val response = chatApi.createGroup(CreateGroupBody(title, memberIds))
                val entity = response.toEntity(existingLastReadSequence = 0)
                conversationDao.upsert(entity)
                ChatResult.Success(entity.toDomain())
            } catch (e: IOException) {
                ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
            } catch (e: HttpException) {
                when (e.code()) {
                    404 -> ChatResult.Failure(ChatFailureReason.USER_NOT_FOUND)
                    400 -> ChatResult.Failure(ChatFailureReason.INVALID_GROUP)
                    else -> ChatResult.Failure(ChatFailureReason.UNKNOWN)
                }
            }
        }

    override suspend fun listMembers(conversationId: String): ChatResult<List<ConversationMember>> =
        withContext(Dispatchers.IO) {
            try {
                ChatResult.Success(chatApi.listMembers(conversationId).map { it.toDomain() })
            } catch (e: IOException) {
                ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
            } catch (e: Exception) {
                ChatResult.Failure(ChatFailureReason.UNKNOWN)
            }
        }

    override suspend fun addMembers(conversationId: String, memberPhoneNumbers: List<String>): ChatResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val memberIds = memberPhoneNumbers.map { chatApi.lookupUserByPhone(it).id }
                chatApi.addMembers(conversationId, AddMembersBody(memberIds))
                ChatResult.Success(Unit)
            } catch (e: IOException) {
                ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
            } catch (e: HttpException) {
                when (e.code()) {
                    404 -> ChatResult.Failure(ChatFailureReason.USER_NOT_FOUND)
                    400 -> ChatResult.Failure(ChatFailureReason.INVALID_GROUP)
                    else -> ChatResult.Failure(ChatFailureReason.UNKNOWN)
                }
            }
        }

    override suspend fun removeMember(conversationId: String, userId: String): ChatResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                chatApi.removeMember(conversationId, userId)
                // Phase 6: if this call removed the SIGNED-IN user (i.e.
                // this was a "leave group" call, not "remove someone
                // else"), also drop the local Room copy — the socket-driven
                // path (handleRealtimeEvent's GroupMemberRemoved/Left
                // branches) only fires for events caused by OTHER devices'
                // actions, never for this device's own outbound request, so
                // this local cleanup is not redundant with that one.
                if (userId == selfUserId()) {
                    conversationDao.deleteById(conversationId)
                }
                ChatResult.Success(Unit)
            } catch (e: IOException) {
                ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
            } catch (e: HttpException) {
                when (e.code()) {
                    403 -> ChatResult.Failure(ChatFailureReason.NOT_GROUP_OWNER)
                    else -> ChatResult.Failure(ChatFailureReason.UNKNOWN)
                }
            }
        }

    override suspend fun renameGroup(conversationId: String, title: String): ChatResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                chatApi.renameGroup(conversationId, RenameGroupBody(title))
                val existing = conversationDao.getById(conversationId)
                if (existing != null) {
                    conversationDao.upsert(existing.copy(title = title))
                }
                ChatResult.Success(Unit)
            } catch (e: IOException) {
                ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
            } catch (e: HttpException) {
                when (e.code()) {
                    400 -> ChatResult.Failure(ChatFailureReason.INVALID_GROUP)
                    else -> ChatResult.Failure(ChatFailureReason.UNKNOWN)
                }
            }
        }

    override suspend fun syncMessages(conversationId: String): ChatResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val after = messageDao.highestSequence(conversationId) ?: 0L
            val remote = chatApi.listMessages(conversationId, after = after, limit = 200)
            // These are, by construction, messages not yet in local DB (the
            // whole point of the `after` cursor) — there is no existing
            // local attachment cache to preserve for any of them.
            messageDao.upsertAll(remote.map { it.toEntity(existingLocalContentUri = null, existingLocalThumbnailUri = null) })
            ChatResult.Success(Unit)
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: Exception) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    override suspend fun sendMessage(conversationId: String, body: String) = withContext(Dispatchers.IO) {
        val selfId = selfUserId() ?: return@withContext
        val clientMsgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Written to Room immediately, before any network call — this is
        // what makes send feel instant (spec 17: optimistic UI) and is
        // what the chat screen is actually rendering a beat later, via
        // observeMessages()'s Flow re-emitting.
        messageDao.upsert(
            MessageEntity(
                clientMsgId = clientMsgId,
                conversationId = conversationId,
                senderId = selfId,
                serverId = null,
                kind = "text",
                body = body,
                sequence = null,
                createdAtEpochMillis = now,
                state = MessageState.PENDING.name,
                attachmentId = null,
                attachmentMimeType = null,
                attachmentOriginalFilename = null,
                attachmentSizeBytes = null,
                attachmentWidthPx = null,
                attachmentHeightPx = null,
                attachmentDurationMs = null,
                attachmentHasThumbnail = false,
                localContentUri = null,
                localThumbnailUri = null,
            ),
        )

        val payload = SendMessagePayload(conversationId, clientMsgId, body)
        outboxDao.upsert(
            OutboxEntity(
                operationId = clientMsgId,
                operationType = OutboxOperationType.SEND_MESSAGE,
                conversationId = conversationId,
                payloadJson = moshi.adapter(SendMessagePayload::class.java).toJson(payload),
                createdAtEpochMillis = now,
                retryCount = 0,
                lastAttemptAtEpochMillis = null,
                state = OutboxState.PENDING.name,
            ),
        )

        outboxSyncScheduler.scheduleNow()
    }

    override suspend fun sendAttachment(
        conversationId: String,
        contentUri: String,
        mimeType: String,
        originalFilename: String,
        caption: String?,
        durationMs: Long?,
        thumbnailLocalUri: String?,
        widthPx: Int?,
        heightPx: Int?,
    ) = withContext(Dispatchers.IO) {
        val selfId = selfUserId() ?: return@withContext
        val clientMsgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Copy into app-private storage BEFORE writing anything to Room —
        // if this throws (e.g. the picker Uri is already unreadable), no
        // half-written message/outbox row is left behind.
        val extension = originalFilename.substringAfterLast('.', missingDelimiterValue = "")
            .let { if (it.isNotEmpty()) ".$it" else null }
        val localPath = attachmentCache.copyToCache(contentUri, extension)
        val sizeBytes = attachmentCache.sizeOf(localPath)
        val isImage = mimeType in setOf("image/jpeg", "image/png", "image/webp")
        val isAudio = mimeType in setOf("audio/mp4", "audio/m4a", "audio/aac", "audio/webm", "audio/ogg", "audio/mpeg")
        val isVideo = mimeType in setOf("video/mp4", "video/webm", "video/3gpp")
        val kind = when {
            isImage -> "image"
            isAudio -> "audio"
            isVideo -> "video"
            else -> "file"
        }

        messageDao.upsert(
            MessageEntity(
                clientMsgId = clientMsgId,
                conversationId = conversationId,
                senderId = selfId,
                serverId = null,
                kind = kind,
                body = caption,
                sequence = null,
                createdAtEpochMillis = now,
                state = MessageState.PENDING.name,
                attachmentId = null, // assigned once the Outbox worker's upload succeeds
                attachmentMimeType = mimeType,
                attachmentOriginalFilename = originalFilename,
                attachmentSizeBytes = sizeBytes,
                // For video these are the client-extracted values (see
                // VideoMetadataExtractor); for image, null here until the
                // server reports the real values on upload, same as before.
                attachmentWidthPx = widthPx,
                attachmentHeightPx = heightPx,
                attachmentDurationMs = durationMs,
                attachmentHasThumbnail = thumbnailLocalUri != null,
                localContentUri = localPath,
                localThumbnailUri = thumbnailLocalUri,
            ),
        )

        val payload = SendAttachmentMessagePayload(
            conversationId = conversationId,
            clientMsgId = clientMsgId,
            localContentUri = localPath,
            mimeType = mimeType,
            originalFilename = originalFilename,
            caption = caption,
            durationMs = durationMs,
            thumbnailLocalPath = thumbnailLocalUri,
            widthPx = widthPx,
            heightPx = heightPx,
        )
        outboxDao.upsert(
            OutboxEntity(
                operationId = clientMsgId,
                operationType = OutboxOperationType.SEND_ATTACHMENT_MESSAGE,
                conversationId = conversationId,
                payloadJson = moshi.adapter(SendAttachmentMessagePayload::class.java).toJson(payload),
                createdAtEpochMillis = now,
                retryCount = 0,
                lastAttemptAtEpochMillis = null,
                state = OutboxState.PENDING.name,
            ),
        )

        outboxSyncScheduler.scheduleNow()
    }

    override suspend fun markRead(conversationId: String, upToSequence: Long) = withContext(Dispatchers.IO) {
        conversationDao.updateLastReadSequence(conversationId, upToSequence)
        try {
            chatApi.markRead(conversationId, MarkReadBody(upToSequence))
        } catch (e: Exception) {
            // Best-effort, same reasoning as AuthRepositoryImpl.logout: the
            // local read cursor already moved: a flaky network shouldn't
            // block the UI. The next successful markRead/sync call catches
            // the server up.
        }
    }

    override suspend fun editMessage(conversationId: String, serverId: String, body: String) = withContext(Dispatchers.IO) {
        messageDao.markEdited(serverId, body, System.currentTimeMillis())
        queueSimpleOperation(
            operationId = "edit-$serverId-${System.currentTimeMillis()}",
            operationType = OutboxOperationType.EDIT_MESSAGE,
            conversationId = conversationId,
            payloadJson = moshi.adapter(EditMessagePayload::class.java).toJson(EditMessagePayload(conversationId, serverId, body)),
        )
    }

    override suspend fun deleteMessage(conversationId: String, serverId: String) = withContext(Dispatchers.IO) {
        messageDao.markDeleted(serverId, System.currentTimeMillis())
        queueSimpleOperation(
            operationId = "delete-$serverId",
            operationType = OutboxOperationType.DELETE_MESSAGE,
            conversationId = conversationId,
            payloadJson = moshi.adapter(DeleteMessagePayload::class.java).toJson(DeleteMessagePayload(conversationId, serverId)),
        )
    }

    override suspend fun setReaction(conversationId: String, serverId: String, emoji: String) = withContext(Dispatchers.IO) {
        val selfId = selfUserId() ?: return@withContext
        val existing = messageDao.getByServerId(serverId)
        val current = existing?.reactionsJson.toReactionSummaries(selfId)
        // Optimistic local aggregate: drop the caller's previous reaction
        // (if any — one active reaction per user, matching the server
        // model) and add the new one. This is a best-effort local mirror
        // of what the server's GROUP BY query will compute — it's
        // overwritten with the server's real aggregate as soon as the
        // outbox entry succeeds or a reaction.updated event arrives from
        // another device.
        val without = current.filterNot { it.reactedByMe }.map { ReactionSummaryLocal(it.emoji, it.userIds) }
        val updated = without + ReactionSummaryLocal(emoji, listOf(selfId))
        messageDao.updateReactions(serverId, reactionsListAdapter.toJson(updated))
        queueSimpleOperation(
            operationId = "react-$serverId-$selfId",
            operationType = OutboxOperationType.SET_REACTION,
            conversationId = conversationId,
            payloadJson = moshi.adapter(SetReactionPayload::class.java).toJson(SetReactionPayload(conversationId, serverId, emoji)),
        )
    }

    override suspend fun clearReaction(conversationId: String, serverId: String) = withContext(Dispatchers.IO) {
        val selfId = selfUserId() ?: return@withContext
        val existing = messageDao.getByServerId(serverId)
        val current = existing?.reactionsJson.toReactionSummaries(selfId)
        val without = current.filterNot { it.reactedByMe }.map { ReactionSummaryLocal(it.emoji, it.userIds) }
        messageDao.updateReactions(serverId, if (without.isEmpty()) null else reactionsListAdapter.toJson(without))
        queueSimpleOperation(
            operationId = "unreact-$serverId-$selfId",
            operationType = OutboxOperationType.CLEAR_REACTION,
            conversationId = conversationId,
            payloadJson = moshi.adapter(ClearReactionPayload::class.java).toJson(ClearReactionPayload(conversationId, serverId)),
        )
    }

    /**
     * Same optimistic-local-first shape as sendMessage: writes a PENDING
     * message into the target conversation immediately (so it appears in
     * the UI before any network round trip), then queues a durable Outbox
     * entry. Text-only — see MessagesService.forward on the backend for
     * why a forwarded attachment isn't supported yet; the UI is expected
     * to only offer "Forward" on a text message in the first place.
     */
    override suspend fun forwardMessage(sourceConversationId: String, sourceServerId: String, targetConversationId: String) =
        withContext(Dispatchers.IO) {
            val selfId = selfUserId() ?: return@withContext
            val source = messageDao.getByServerId(sourceServerId) ?: return@withContext
            val clientMsgId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            messageDao.upsert(
                MessageEntity(
                    clientMsgId = clientMsgId,
                    conversationId = targetConversationId,
                    senderId = selfId,
                    serverId = null,
                    kind = "text",
                    body = source.body,
                    sequence = null,
                    createdAtEpochMillis = now,
                    state = MessageState.PENDING.name,
                    attachmentId = null,
                    attachmentMimeType = null,
                    attachmentOriginalFilename = null,
                    attachmentSizeBytes = null,
                    attachmentWidthPx = null,
                    attachmentHeightPx = null,
                    attachmentDurationMs = null,
                    attachmentHasThumbnail = false,
                    localContentUri = null,
                    localThumbnailUri = null,
                    forwardedFromMessageId = sourceServerId,
                ),
            )

            val payload = ForwardMessagePayload(targetConversationId, sourceServerId, clientMsgId)
            outboxDao.upsert(
                OutboxEntity(
                    operationId = clientMsgId,
                    operationType = OutboxOperationType.FORWARD_MESSAGE,
                    conversationId = targetConversationId,
                    payloadJson = moshi.adapter(ForwardMessagePayload::class.java).toJson(payload),
                    createdAtEpochMillis = now,
                    retryCount = 0,
                    lastAttemptAtEpochMillis = null,
                    state = OutboxState.PENDING.name,
                ),
            )
            outboxSyncScheduler.scheduleNow()
        }

    /** Shared tail end of editMessage/deleteMessage/setReaction/clearReaction — all four queue a fire-and-forget outbox entry with no accompanying message-row creation (the row already exists; only its state changed). */
    private suspend fun queueSimpleOperation(operationId: String, operationType: String, conversationId: String, payloadJson: String) {
        outboxDao.upsert(
            OutboxEntity(
                operationId = operationId,
                operationType = operationType,
                conversationId = conversationId,
                payloadJson = payloadJson,
                createdAtEpochMillis = System.currentTimeMillis(),
                retryCount = 0,
                lastAttemptAtEpochMillis = null,
                state = OutboxState.PENDING.name,
            ),
        )
        outboxSyncScheduler.scheduleNow()
    }

    override fun connectRealtime() {
        repositoryScope.launch {
            val tokens = tokenStore.read() ?: return@launch
            realtimeClient.connect(tokens.accessToken)
        }
        if (eventCollectionJob?.isActive != true) {
            eventCollectionJob = repositoryScope.launch {
                realtimeClient.events.collect { event -> handleRealtimeEvent(event) }
            }
        }
    }

    override fun disconnectRealtime() {
        realtimeClient.disconnect()
        eventCollectionJob?.cancel()
        eventCollectionJob = null
    }

    private suspend fun handleRealtimeEvent(event: ServerToClientEvent) {
        when (event) {
            is ServerToClientEvent.MessageCreated -> {
                // Always from another member (the server never echoes
                // message.created back to the sender — see
                // chat.gateway.ts) — this device never has a pre-existing
                // local cache for someone else's attachment.
                messageDao.upsert(event.message.toEntity(existingLocalContentUri = null, existingLocalThumbnailUri = null))
                conversationDao.updateLastMessageAt(
                    event.message.conversationId,
                    java.time.Instant.parse(event.message.createdAt).toEpochMilli(),
                )
            }
            is ServerToClientEvent.MessageUpdated -> {
                // Preserves local cache fields the same way MessageCreated
                // does — an edit from another member never carries this
                // device's own localContentUri/localThumbnailUri.
                val existing = messageDao.getByServerId(event.message.id)
                messageDao.upsert(
                    event.message.toEntity(
                        existingLocalContentUri = existing?.localContentUri,
                        existingLocalThumbnailUri = existing?.localThumbnailUri,
                    ),
                )
            }
            is ServerToClientEvent.MessageDeleted -> {
                messageDao.markDeleted(event.messageId, java.time.Instant.parse(event.deletedAt).toEpochMilli())
            }
            is ServerToClientEvent.ReactionUpdated -> {
                messageDao.updateReactions(event.messageId, event.reactions.toReactionsJson())
            }
            is ServerToClientEvent.MessageRead -> {
                val selfId = selfUserId()
                if (selfId != null && event.userId != selfId) {
                    // The OTHER party just told us they've read up to this
                    // sequence — that means OUR OWN sent messages up to
                    // there are now read, not theirs.
                    messageDao.markReadUpTo(
                        event.conversationId,
                        selfId,
                        event.lastReadSequence,
                        MessageState.READ.name,
                    )
                }
            }
            is ServerToClientEvent.TypingStarted ->
                _typingConversationIds.value = _typingConversationIds.value + event.conversationId
            is ServerToClientEvent.TypingStopped ->
                _typingConversationIds.value = _typingConversationIds.value - event.conversationId

            // --- Phase 6: group realtime completion ---

            is ServerToClientEvent.ConversationAdded -> {
                // A brand-new group this device was just added to — insert
                // straight into Room; observeConversations()'s Flow picks
                // it up on its own, same as any other Room write.
                conversationDao.upsert(event.conversation.toEntity())
            }
            is ServerToClientEvent.GroupMemberAdded -> {
                // The conversation row itself (title, etc.) hasn't changed
                // for existing members — only the roster has, and rosters
                // are always a live fetch (see ChatRepository.listMembers's
                // doc comment), never cached in Room. So there's nothing to
                // write here; just tell any open Group Info screen to
                // reload its member list.
                _groupChanges.emit(GroupChangeSignal.MembershipChanged(event.conversationId))
            }
            is ServerToClientEvent.GroupMemberRemoved -> {
                val selfId = selfUserId()
                if (selfId != null && event.userId == selfId) {
                    conversationDao.deleteById(event.conversationId)
                    _groupChanges.emit(GroupChangeSignal.SelfRemoved(event.conversationId))
                } else {
                    _groupChanges.emit(GroupChangeSignal.MembershipChanged(event.conversationId))
                }
            }
            is ServerToClientEvent.GroupMemberLeft -> {
                val selfId = selfUserId()
                if (selfId != null && event.userId == selfId) {
                    // Reachable if this same account is signed in on
                    // another device and left the group there — this
                    // device also needs to drop its local copy.
                    conversationDao.deleteById(event.conversationId)
                    _groupChanges.emit(GroupChangeSignal.SelfRemoved(event.conversationId))
                } else {
                    _groupChanges.emit(GroupChangeSignal.MembershipChanged(event.conversationId))
                }
            }
            is ServerToClientEvent.GroupRenamed -> {
                conversationDao.updateTitle(event.conversationId, event.title)
                _groupChanges.emit(GroupChangeSignal.Renamed(event.conversationId, event.title))
            }
        }
    }

    private suspend fun selfUserId(): String? =
        cachedSelfUserId ?: userDao.getSelfOnce()?.id?.also { cachedSelfUserId = it }
}
