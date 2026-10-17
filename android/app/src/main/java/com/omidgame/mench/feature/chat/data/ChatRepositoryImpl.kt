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
import com.omidgame.mench.core.media.ImageCompressor
import com.omidgame.mench.core.media.UploadProgress
import com.omidgame.mench.core.network.AddMembersBody
import com.omidgame.mench.core.network.ChangeRoleBody
import com.omidgame.mench.core.network.CreateInviteBody
import com.omidgame.mench.core.network.CreatePollBody
import com.omidgame.mench.core.network.GroupInfoWire
import com.omidgame.mench.core.network.InviteWire
import com.omidgame.mench.core.network.UpdateGroupBody
import com.omidgame.mench.feature.chat.domain.GroupInfo
import com.omidgame.mench.feature.chat.domain.GroupInvite
import com.omidgame.mench.feature.chat.domain.GroupRole
import com.omidgame.mench.feature.chat.domain.GroupSettings
import com.omidgame.mench.feature.chat.domain.InvitePreview
import com.omidgame.mench.core.network.ScheduleMessageBody
import com.omidgame.mench.core.network.ScheduledMessageWire
import com.omidgame.mench.feature.chat.domain.ScheduledMessage
import com.omidgame.mench.core.network.PollWire
import com.omidgame.mench.core.network.VotePollBody
import com.omidgame.mench.feature.chat.domain.Poll
import com.omidgame.mench.feature.chat.domain.PollOption
import com.omidgame.mench.core.network.ChatApi
import com.omidgame.mench.core.network.CreateDirectConversationBody
import com.omidgame.mench.core.network.CreateGroupBody
import com.omidgame.mench.core.network.MarkReadBody
import com.omidgame.mench.core.network.RenameGroupBody
import com.omidgame.mench.core.network.UpdateConversationSettingsBody
import com.omidgame.mench.core.network.UpdateUsBody
import com.omidgame.mench.feature.chat.domain.Wish
import com.omidgame.mench.feature.chat.domain.LinkPreview
import com.omidgame.mench.feature.chat.domain.Question
import com.omidgame.mench.core.network.WishWire
import com.omidgame.mench.core.network.UpdateWishBody
import com.omidgame.mench.core.network.PinBody
import com.omidgame.mench.core.network.CreateWishBody
import com.omidgame.mench.core.network.AnswerBody
import com.omidgame.mench.feature.chat.domain.Moods
import com.omidgame.mench.feature.chat.domain.Mood
import com.omidgame.mench.core.network.UpdateMoodBody
import com.omidgame.mench.core.network.MoodWire
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.omidgame.mench.feature.chat.domain.Counter
import com.omidgame.mench.feature.chat.domain.Memory
import com.omidgame.mench.core.network.MemoryWire
import com.omidgame.mench.core.network.CreateMemoryBody
import com.omidgame.mench.core.network.AffectionBody
import com.omidgame.mench.core.network.HeartbeatBody
import com.omidgame.mench.core.network.ScreenEffectBody
import com.omidgame.mench.feature.chat.domain.Letter
import com.omidgame.mench.core.network.LetterWire
import com.omidgame.mench.core.network.CreateLetterBody
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
import com.omidgame.mench.feature.chat.domain.UserPresence
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
import kotlinx.coroutines.flow.emitAll
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
    private var reconnectSyncJob: Job? = null
    private var cachedSelfUserId: String? = null

    private val _typingConversationIds = MutableStateFlow<Set<String>>(emptySet())

    override fun observeTypingConversationIds(): Flow<Set<String>> = _typingConversationIds

    override suspend fun linkPreview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        // Settings Phase 2: link previews are fetched by the server, so the user can switch them off (privacy).
        if (!com.omidgame.mench.feature.chat.presentation.ChatSettingsStore.current.linkPreviews) return@withContext null
        try {
            chatApi.linkPreview(url).takeIf { it.title != null || it.description != null }
                ?.let { LinkPreview(it.url, it.title, it.description, it.siteName) }
        } catch (e: Exception) {
            null
        }
    }

    private val _questionChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)
    private val _wishlistChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeQuestionChanges(): Flow<String> = _questionChanges

    override fun observeWishlistChanges(): Flow<String> = _wishlistChanges

    override suspend fun getQuestion(conversationId: String): ChatResult<Question> = letterCall {
        chatApi.getQuestion(conversationId).let { Question(it.date, it.question, it.myAnswer, it.partnerAnswered, it.partnerAnswer, it.streak, it.bestStreak) }
    }

    override suspend fun answerQuestion(conversationId: String, answer: String): ChatResult<Unit> =
        letterCall { chatApi.answerQuestion(conversationId, AnswerBody(answer)) }

    private fun WishWire.toDomain() = Wish(id = id, title = title, done = done, isMine = isMine)

    override suspend fun listWishes(conversationId: String): ChatResult<List<Wish>> =
        letterCall { chatApi.listWishes(conversationId).map { it.toDomain() } }

    override suspend fun createWish(conversationId: String, title: String): ChatResult<Wish> =
        letterCall { chatApi.createWish(conversationId, CreateWishBody(title)).toDomain() }

    override suspend fun setWishDone(conversationId: String, wishId: String, done: Boolean): ChatResult<Wish> =
        letterCall { chatApi.updateWish(conversationId, wishId, UpdateWishBody(done)).toDomain() }

    override suspend fun deleteWish(conversationId: String, wishId: String): ChatResult<Unit> =
        letterCall { chatApi.deleteWish(conversationId, wishId) }

    override suspend fun setPinnedMessage(conversationId: String, serverMessageId: String?): ChatResult<Unit> =
        letterCall {
            chatApi.pinMessage(conversationId, PinBody(serverMessageId))
            conversationDao.updatePinnedMessage(conversationId, serverMessageId)
        }

    private val _moodChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeMoodChanges(): Flow<String> = _moodChanges

    private fun MoodWire.toDomain() = Mood(
        emoji = emoji,
        text = text,
        setAtEpochMillis = runCatching { java.time.Instant.parse(setAt).toEpochMilli() }.getOrDefault(0L),
    )

    override suspend fun getMoods(conversationId: String): ChatResult<Moods> =
        letterCall { chatApi.getMoods(conversationId).let { Moods(it.mine?.toDomain(), it.other?.toDomain()) } }

    override suspend fun setMood(conversationId: String, emoji: String, text: String?): ChatResult<Unit> =
        letterCall { chatApi.setMood(conversationId, UpdateMoodBody(emoji, text)) }

    override suspend fun loadYearMessageStats(conversationId: String, year: Int): com.omidgame.mench.feature.chat.domain.YearMessageStats =
        withContext(Dispatchers.IO) {
            val selfId = selfUserId()
            val zone = java.time.ZoneId.systemDefault()
            val since = java.time.LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val rows = messageDao.yearTextRows(conversationId, since).mapNotNull { r ->
                r.body?.let { com.omidgame.mench.feature.chat.domain.YearMessageRow(it, r.senderId == selfId, r.createdAtEpochMillis) }
            }
            com.omidgame.mench.feature.chat.domain.computeYearMessageStats(rows, zone)
        }

    private val _quizChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeQuizChanges(): Flow<String> = _quizChanges

    override suspend fun listQuizzes(conversationId: String): ChatResult<List<com.omidgame.mench.feature.chat.domain.QuizSummary>> =
        letterCall {
            chatApi.listQuizzes(conversationId).map {
                com.omidgame.mench.feature.chat.domain.QuizSummary(it.id, it.title, it.isMine, it.questionCount, it.myScore, it.myTotal, it.partnerScore, it.partnerTotal)
            }
        }

    override suspend fun createQuiz(
        conversationId: String,
        title: String,
        questions: List<com.omidgame.mench.feature.chat.domain.QuizQuestion>,
    ): ChatResult<com.omidgame.mench.feature.chat.domain.QuizSummary> = letterCall {
        val w = chatApi.createQuiz(
            conversationId,
            com.omidgame.mench.core.network.CreateQuizBody(
                title,
                questions.map { com.omidgame.mench.core.network.QuizQuestionWire(it.text, it.options, it.correct) },
            ),
        )
        com.omidgame.mench.feature.chat.domain.QuizSummary(w.id, w.title, w.isMine, w.questionCount, w.myScore, w.myTotal, w.partnerScore, w.partnerTotal)
    }

    override suspend fun getQuiz(conversationId: String, quizId: String): ChatResult<com.omidgame.mench.feature.chat.domain.QuizDetail> =
        letterCall {
            val w = chatApi.getQuiz(conversationId, quizId)
            com.omidgame.mench.feature.chat.domain.QuizDetail(
                w.id, w.title, w.isMine, w.answered,
                w.questions.map { com.omidgame.mench.feature.chat.domain.QuizQuestion(it.q, it.options, it.correct) },
            )
        }

    override suspend fun answerQuiz(conversationId: String, quizId: String, answers: List<Int>): ChatResult<com.omidgame.mench.feature.chat.domain.QuizResult> =
        letterCall {
            val w = chatApi.answerQuiz(conversationId, quizId, com.omidgame.mench.core.network.AnswerQuizBody(answers))
            com.omidgame.mench.feature.chat.domain.QuizResult(w.score, w.total, w.correct)
        }

    override suspend fun deleteQuiz(conversationId: String, quizId: String): ChatResult<Unit> =
        letterCall { chatApi.deleteQuiz(conversationId, quizId) }

    private val _vaultChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeVaultChanges(): Flow<String> = _vaultChanges

    override suspend fun listVault(conversationId: String): ChatResult<List<com.omidgame.mench.feature.chat.domain.VaultItem>> =
        letterCall { chatApi.listVault(conversationId).map { com.omidgame.mench.feature.chat.domain.VaultItem(it.id, it.blob, it.isMine) } }

    override suspend fun createVaultItem(conversationId: String, blob: String): ChatResult<com.omidgame.mench.feature.chat.domain.VaultItem> =
        letterCall {
            val w = chatApi.createVaultItem(conversationId, com.omidgame.mench.core.network.CreateVaultItemBody(blob))
            com.omidgame.mench.feature.chat.domain.VaultItem(w.id, w.blob, w.isMine)
        }

    override suspend fun deleteVaultItem(conversationId: String, itemId: String): ChatResult<Unit> =
        letterCall { chatApi.deleteVaultItem(conversationId, itemId) }

    override suspend fun unseenStoryConversations(): ChatResult<Set<String>> =
        letterCall { chatApi.unseenStories().map { it.conversationId }.toSet() }

    /** Uploads a picked photo as an attachment of [conversationId] and returns the attachment id. Throws on failure (callers wrap in letterCall). */
    private suspend fun uploadPhoto(conversationId: String, photo: com.omidgame.mench.feature.chat.domain.PickedPhoto): String {
        val name = photo.filename ?: "photo.jpg"
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").let { if (it.isNotEmpty()) ".$it" else null }
        val localPath = attachmentCache.copyToCache(photo.uri, extension)
        val file = java.io.File(localPath)
        return try {
            val part = okhttp3.MultipartBody.Part.createFormData("file", name, file.asRequestBody(photo.mimeType.toMediaTypeOrNull()))
            chatApi.uploadAttachment(conversationId, part).id
        } finally {
            file.delete()
        }
    }

    private val _albumChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeAlbumChanges(): Flow<String> = _albumChanges

    private fun com.omidgame.mench.core.network.AlbumItemWire.toDomain() =
        com.omidgame.mench.feature.chat.domain.AlbumItem(id, attachmentId, caption, isMine, myReaction, otherReaction)

    override suspend fun listAlbum(conversationId: String): ChatResult<List<com.omidgame.mench.feature.chat.domain.AlbumItem>> =
        letterCall { chatApi.listAlbum(conversationId).map { it.toDomain() } }

    override suspend fun addAlbumPhoto(
        conversationId: String,
        photo: com.omidgame.mench.feature.chat.domain.PickedPhoto,
        caption: String?,
    ): ChatResult<com.omidgame.mench.feature.chat.domain.AlbumItem> = letterCall {
        val attachmentId = uploadPhoto(conversationId, photo)
        chatApi.createAlbumItem(conversationId, com.omidgame.mench.core.network.CreateAlbumBody(attachmentId, caption)).toDomain()
    }

    override suspend fun reactAlbumItem(conversationId: String, itemId: String, emoji: String?): ChatResult<Unit> =
        letterCall { chatApi.reactAlbumItem(conversationId, itemId, com.omidgame.mench.core.network.ReactAlbumBody(emoji)) }

    override suspend fun deleteAlbumItem(conversationId: String, itemId: String): ChatResult<Unit> =
        letterCall { chatApi.deleteAlbumItem(conversationId, itemId) }

    private val _storyChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeStoryChanges(): Flow<String> = _storyChanges

    private fun com.omidgame.mench.core.network.StoryWire.toDomain() =
        com.omidgame.mench.feature.chat.domain.Story(id, body, emoji, palette, isMine, seen, views, attachmentId)

    override suspend fun listStories(conversationId: String): ChatResult<List<com.omidgame.mench.feature.chat.domain.Story>> =
        letterCall { chatApi.listStories(conversationId).map { it.toDomain() } }

    override suspend fun createStory(
        conversationId: String,
        body: String,
        emoji: String?,
        palette: Int,
        photo: com.omidgame.mench.feature.chat.domain.PickedPhoto?,
    ): ChatResult<com.omidgame.mench.feature.chat.domain.Story> =
        letterCall {
            val attachmentId = photo?.let { uploadPhoto(conversationId, it) }
            chatApi.createStory(conversationId, com.omidgame.mench.core.network.CreateStoryBody(body, emoji, palette, attachmentId)).toDomain()
        }

    override suspend fun viewStory(conversationId: String, storyId: String): ChatResult<Unit> =
        letterCall { chatApi.viewStory(conversationId, storyId) }

    override suspend fun deleteStory(conversationId: String, storyId: String): ChatResult<Unit> =
        letterCall { chatApi.deleteStory(conversationId, storyId) }

    private val _canvasChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeCanvasChanges(): Flow<String> = _canvasChanges

    override suspend fun getCanvas(conversationId: String): ChatResult<List<com.omidgame.mench.feature.chat.domain.CanvasStroke>> =
        letterCall {
            chatApi.getCanvas(conversationId).strokes.map {
                com.omidgame.mench.feature.chat.domain.CanvasStroke(it.color, it.width, it.points)
            }
        }

    override suspend fun appendCanvasStrokes(
        conversationId: String,
        strokes: List<com.omidgame.mench.feature.chat.domain.CanvasStroke>,
    ): ChatResult<Unit> = letterCall {
        chatApi.appendCanvasStrokes(
            conversationId,
            com.omidgame.mench.core.network.AppendStrokesBody(
                strokes.map { com.omidgame.mench.core.network.StrokeWire(it.color, it.width, it.points) },
            ),
        )
    }

    override suspend fun clearCanvas(conversationId: String): ChatResult<Unit> =
        letterCall { chatApi.clearCanvas(conversationId) }

    private val _counterChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeCounterChanges(): Flow<String> = _counterChanges

    private fun com.omidgame.mench.core.network.CounterWire.toDomain() = Counter(
        id = id,
        title = title,
        emoji = emoji,
        kind = kind,
        anchorDate = anchorDate,
        tallyCount = tallyCount,
        isMine = isMine,
        theme = theme,
    )

    override suspend fun listCounters(conversationId: String): ChatResult<List<Counter>> =
        letterCall { chatApi.listCounters(conversationId).map { it.toDomain() } }

    override suspend fun createCounter(
        conversationId: String,
        title: String,
        emoji: String?,
        kind: String,
        anchorDateIso: String?,
        theme: Int,
    ): ChatResult<Counter> =
        letterCall { chatApi.createCounter(conversationId, com.omidgame.mench.core.network.CreateCounterBody(title, emoji, kind, anchorDateIso, theme)).toDomain() }

    override suspend fun bumpCounter(conversationId: String, counterId: String, delta: Int): ChatResult<Counter> =
        letterCall { chatApi.bumpCounter(conversationId, counterId, com.omidgame.mench.core.network.BumpCounterBody(delta)).toDomain() }

    override suspend fun deleteCounter(conversationId: String, counterId: String): ChatResult<Unit> =
        letterCall { chatApi.deleteCounter(conversationId, counterId) }

    private val _memoryChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeMemoryChanges(): Flow<String> = _memoryChanges

    private val _affection = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, String>>(replay = 0, extraBufferCapacity = 4)

    override fun observeAffection(): Flow<Pair<String, String>> = _affection

    override suspend fun sendAffection(conversationId: String, kind: String): ChatResult<Unit> =
        letterCall { chatApi.sendAffection(conversationId, AffectionBody(kind)) }

    private fun MemoryWire.toDomain() = Memory(
        id = id,
        title = title,
        note = note,
        memoryDate = memoryDate,
        attachmentId = attachmentId,
        isMine = isMine,
    )

    override suspend fun listMemories(conversationId: String): ChatResult<List<Memory>> =
        letterCall { chatApi.listMemories(conversationId).map { it.toDomain() } }

    override suspend fun createMemory(
        conversationId: String,
        title: String,
        note: String?,
        memoryDateIso: String,
        photoUri: String?,
        photoMimeType: String?,
        photoFilename: String?,
    ): ChatResult<Memory> = letterCall {
        var attachmentId: String? = null
        if (photoUri != null && photoMimeType != null) {
            val name = photoFilename ?: "memory.jpg"
            val extension = name.substringAfterLast('.', missingDelimiterValue = "")
                .let { if (it.isNotEmpty()) ".$it" else null }
            val localPath = attachmentCache.copyToCache(photoUri, extension)
            val file = java.io.File(localPath)
            val part = okhttp3.MultipartBody.Part.createFormData(
                "file",
                name,
                file.asRequestBody(photoMimeType.toMediaTypeOrNull()),
            )
            attachmentId = chatApi.uploadAttachment(conversationId, part).id
            file.delete()
        }
        chatApi.createMemory(conversationId, CreateMemoryBody(title, note, memoryDateIso, attachmentId)).toDomain()
    }

    override suspend fun deleteMemory(conversationId: String, memoryId: String): ChatResult<Unit> =
        letterCall { chatApi.deleteMemory(conversationId, memoryId) }

    private val _letterChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeLetterChanges(): Flow<String> = _letterChanges

    private fun LetterWire.toDomain() = Letter(
        id = id,
        title = title,
        body = body,
        unlockAtEpochMillis = unlockAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
        openedAtEpochMillis = openedAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
        createdAtEpochMillis = runCatching { java.time.Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L),
        isMine = isMine,
        canOpen = canOpen,
        attachmentId = attachmentId,
    )

    private suspend fun <T> letterCall(block: suspend () -> T): ChatResult<T> = withContext(Dispatchers.IO) {
        try {
            ChatResult.Success(block())
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    override suspend fun listLetters(conversationId: String): ChatResult<List<Letter>> =
        letterCall { chatApi.listLetters(conversationId).map { it.toDomain() } }

    override suspend fun createLetter(
        conversationId: String,
        title: String,
        body: String,
        unlockAtIso: String?,
        photo: com.omidgame.mench.feature.chat.domain.PickedPhoto?,
    ): ChatResult<Letter> =
        letterCall {
            val attachmentId = photo?.let { uploadPhoto(conversationId, it) }
            chatApi.createLetter(conversationId, CreateLetterBody(title, body, unlockAtIso, attachmentId)).toDomain()
        }

    override suspend fun openLetter(conversationId: String, letterId: String): ChatResult<Letter> =
        letterCall { chatApi.openLetter(conversationId, letterId).toDomain() }

    private val _missYou = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4)

    override fun observeMissYou(): Flow<String> = _missYou

    private val _heartbeats = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, Long>>(replay = 0, extraBufferCapacity = 4)

    override fun observeHeartbeats(): Flow<Pair<String, Long>> = _heartbeats

    override suspend fun sendHeartbeat(conversationId: String, durationMs: Long): ChatResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            ChatResult.Success(chatApi.sendHeartbeat(conversationId, HeartbeatBody(durationMs)).delivered)
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    private val _screenEffects = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, String>>(replay = 0, extraBufferCapacity = 4)

    override fun observeScreenEffects(): Flow<Pair<String, String>> = _screenEffects

    override suspend fun setSecureChat(conversationId: String, peerUserId: String?, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!enabled) {
            com.omidgame.mench.core.crypto.E2eeManager.setSecureChat(conversationId, null)
            return@withContext true
        }
        if (peerUserId == null) return@withContext false
        com.omidgame.mench.core.crypto.E2eeManager.publish(chatApi)
        val peerKey = com.omidgame.mench.core.crypto.E2eeManager.peerKey(chatApi, peerUserId) ?: return@withContext false
        // A malformed key must never switch the chat to "secure" and then fail every send.
        if (runCatching { com.omidgame.mench.core.crypto.E2eeManager.encrypt("ok", peerKey) }.isFailure) return@withContext false
        com.omidgame.mench.core.crypto.E2eeManager.setSecureChat(conversationId, peerUserId)
        true
    }

    private val _together = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, com.omidgame.mench.feature.chat.domain.TogetherSignal>>(replay = 0, extraBufferCapacity = 16)

    override fun observeTogether(): Flow<Pair<String, com.omidgame.mench.feature.chat.domain.TogetherSignal>> = _together

    override suspend fun sendTogether(conversationId: String, kind: String, attachmentId: String?, positionMs: Long, playing: Boolean): ChatResult<Unit> = withContext(Dispatchers.IO) {
        try {
            chatApi.sendTogether(conversationId, com.omidgame.mench.core.network.TogetherBody(kind, attachmentId, positionMs, playing))
            ChatResult.Success(Unit)
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    override suspend fun sendScreenEffect(conversationId: String, effect: String): ChatResult<Unit> = withContext(Dispatchers.IO) {
        try {
            chatApi.sendScreenEffect(conversationId, ScreenEffectBody(effect))
            ChatResult.Success(Unit)
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    override suspend fun setUsDates(
        conversationId: String,
        togetherSince: String?,
        nextMeetDate: String?,
    ): ChatResult<Unit> = withContext(Dispatchers.IO) {
        try {
            chatApi.updateUs(conversationId, UpdateUsBody(togetherSince, nextMeetDate))
            val existing = conversationDao.getById(conversationId)
            if (existing != null) {
                conversationDao.updateUsDates(
                    conversationId,
                    togetherSince ?: existing.togetherSince,
                    nextMeetDate ?: existing.nextMeetDate,
                )
            }
            ChatResult.Success(Unit)
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    override suspend fun sendMissYou(conversationId: String): ChatResult<Unit> = withContext(Dispatchers.IO) {
        try {
            chatApi.sendMissYou(conversationId)
            ChatResult.Success(Unit)
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    private val _presence = MutableStateFlow<Map<String, UserPresence>>(emptyMap())

    override fun observePresence(): Flow<Map<String, UserPresence>> = _presence

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

    override fun observeConversationPreviews(): Flow<Map<String, com.omidgame.mench.feature.chat.domain.ConversationPreview>> =
        kotlinx.coroutines.flow.flow {
            val selfId = selfUserId()
            if (selfId == null) {
                emit(emptyMap())
            } else {
                emitAll(
                    kotlinx.coroutines.flow.combine(
                        messageDao.observeLastMessages(),
                        messageDao.observeUnreadCounts(selfId),
                    ) { lastRows, unreadRows ->
                        val unread = unreadRows.associate { it.conversationId to it.unread }
                        lastRows.groupBy { it.conversationId }.mapValues { (conversationId, rows) ->
                            val last = rows.maxByOrNull { it.createdAtEpochMillis }!!
                            com.omidgame.mench.feature.chat.domain.ConversationPreview(
                                lastBody = last.body,
                                lastKind = runCatching {
                                    com.omidgame.mench.feature.chat.domain.DomainMessageKind.valueOf(last.kind.uppercase())
                                }.getOrDefault(com.omidgame.mench.feature.chat.domain.DomainMessageKind.TEXT),
                                lastIsOwn = last.senderId == selfId,
                                lastIsDeleted = last.deletedAtEpochMillis != null,
                                lastAtEpochMillis = last.createdAtEpochMillis,
                                unreadCount = unread[conversationId] ?: 0,
                            )
                        }
                    },
                )
            }
        }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observeForConversation(conversationId).map { entities ->
            val selfId = selfUserId()
            entities.map { it.toDomain(selfId) }
        }

    override fun observeMessageWindow(conversationId: String, limit: Int): Flow<List<Message>> =
        messageDao.observeWindow(conversationId, limit).map { entities ->
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

    override suspend fun openSavedMessages(): ChatResult<Conversation> = withContext(Dispatchers.IO) {
        try {
            val response = chatApi.getSavedConversation()
            val existing = conversationDao.getById(response.id)
            val entity = response.toEntity(existing?.lastReadSequence ?: 0)
            conversationDao.upsert(entity)
            ChatResult.Success(entity.toDomain())
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    override suspend fun startDirectConversationWithUser(userId: String): ChatResult<Conversation> =
        withContext(Dispatchers.IO) {
            try {
                val response = chatApi.createDirectConversation(CreateDirectConversationBody(userId))
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

    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): ChatResult<Unit> =
        updateConversationSetting(
            conversationId,
            UpdateConversationSettingsBody(isMuted = muted),
        ) { conversationDao.updateMuted(conversationId, muted) }

    override suspend fun setConversationArchived(conversationId: String, archived: Boolean): ChatResult<Unit> =
        updateConversationSetting(
            conversationId,
            UpdateConversationSettingsBody(isArchived = archived),
        ) { conversationDao.updateArchived(conversationId, archived) }

    override suspend fun setConversationPinned(conversationId: String, pinned: Boolean): ChatResult<Unit> =
        updateConversationSetting(
            conversationId,
            UpdateConversationSettingsBody(isPinned = pinned),
        ) { conversationDao.updatePinned(conversationId, pinned) }

    private suspend fun updateConversationSetting(
        conversationId: String,
        body: UpdateConversationSettingsBody,
        applyLocally: suspend () -> Unit,
    ): ChatResult<Unit> = withContext(Dispatchers.IO) {
        try {
            chatApi.updateConversationSettings(conversationId, body)
            applyLocally()
            ChatResult.Success(Unit)
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: HttpException) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
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
            // Catch-up after being offline: tell the sender we have these too (only others' messages count).
            val selfId = selfUserId()
            remote.filter { it.senderId != selfId }.maxOfOrNull { it.sequence }?.let { ackDelivered(conversationId, it) }
            ChatResult.Success(Unit)
        } catch (e: IOException) {
            ChatResult.Failure(ChatFailureReason.NETWORK_UNAVAILABLE)
        } catch (e: Exception) {
            ChatResult.Failure(ChatFailureReason.UNKNOWN)
        }
    }

    override suspend fun sendMessage(conversationId: String, body: String, replyToMessageId: String?, silent: Boolean) {
        enqueueText(conversationId, body, replyToMessageId, silent)
    }

    override suspend fun startLiveLocation(conversationId: String, body: String): String? =
        enqueueText(conversationId, body, null, false)

    override suspend fun serverIdOf(clientMsgId: String): String? =
        withContext(Dispatchers.IO) { messageDao.getByClientMsgId(clientMsgId)?.serverId }

    private suspend fun enqueueText(conversationId: String, body: String, replyToMessageId: String?, silent: Boolean): String? = withContext(Dispatchers.IO) {
        val selfId = selfUserId() ?: return@withContext null
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
                replyToMessageId = replyToMessageId,
            ),
        )

        val payload = SendMessagePayload(conversationId, clientMsgId, body, replyToMessageId, silent)
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
        clientMsgId
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
        waveform: List<Int>?,
    ) = withContext(Dispatchers.IO) {
        val selfId = selfUserId() ?: return@withContext
        val clientMsgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val waveformBase64 = if (waveform != null) com.omidgame.mench.core.media.Waveform.encode(waveform) else null

        // Copy into app-private storage BEFORE writing anything to Room —
        // if this throws (e.g. the picker Uri is already unreadable), no
        // half-written message/outbox row is left behind.
        val extension = originalFilename.substringAfterLast('.', missingDelimiterValue = "")
            .let { if (it.isNotEmpty()) ".$it" else null }
        val copiedPath = attachmentCache.copyToCache(contentUri, extension)
        // Phase 1B: photos are downscaled/recompressed before they ever hit the Outbox, so the
        // upload is small and the optimistic bubble shows the same bytes the recipient will get.
        val isPhoto = mimeType in setOf("image/jpeg", "image/png", "image/webp")
        // Settings Phase 2: "photo quality = original" skips recompression; location is stripped from originals (default on).
        val photoSettings = com.omidgame.mench.feature.chat.presentation.ChatSettingsStore.current
        val keepOriginal = photoSettings.photoQuality == com.omidgame.mench.feature.settings.domain.PhotoQuality.ORIGINAL
        val compressed = if (isPhoto && !keepOriginal) ImageCompressor.compress(java.io.File(copiedPath)) else null
        if (isPhoto && photoSettings.stripLocation && (compressed == null || !compressed.compressed)) {
            com.omidgame.mench.core.media.MediaPrivacy.stripLocation(java.io.File(copiedPath))
        }
        val localPath = compressed?.file?.absolutePath ?: copiedPath
        val effectiveMime = if (compressed != null && compressed.compressed) compressed.mimeType else mimeType
        val effectiveName = if (compressed != null && compressed.compressed) {
            originalFilename.substringBeforeLast('.', originalFilename) + compressed.extension
        } else {
            originalFilename
        }
        val effectiveWidth = compressed?.takeIf { it.compressed }?.widthPx ?: widthPx
        val effectiveHeight = compressed?.takeIf { it.compressed }?.heightPx ?: heightPx
        val sizeBytes = attachmentCache.sizeOf(localPath)
        val isImage = effectiveMime in setOf("image/jpeg", "image/png", "image/webp")
        val isAudio = effectiveMime in setOf("audio/mp4", "audio/m4a", "audio/aac", "audio/webm", "audio/ogg", "audio/mpeg")
        val isVideo = effectiveMime in setOf("video/mp4", "video/webm", "video/3gpp")
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
                attachmentMimeType = effectiveMime,
                attachmentOriginalFilename = effectiveName,
                attachmentSizeBytes = sizeBytes,
                // For video these are the client-extracted values (see
                // VideoMetadataExtractor); for image, null here until the
                // server reports the real values on upload, same as before.
                attachmentWidthPx = effectiveWidth,
                attachmentHeightPx = effectiveHeight,
                attachmentDurationMs = durationMs,
                attachmentHasThumbnail = thumbnailLocalUri != null,
                localContentUri = localPath,
                localThumbnailUri = thumbnailLocalUri,
                attachmentWaveform = waveformBase64,
            ),
        )

        val payload = SendAttachmentMessagePayload(
            conversationId = conversationId,
            clientMsgId = clientMsgId,
            localContentUri = localPath,
            mimeType = effectiveMime,
            originalFilename = effectiveName,
            caption = caption,
            durationMs = durationMs,
            thumbnailLocalPath = thumbnailLocalUri,
            widthPx = effectiveWidth,
            heightPx = effectiveHeight,
            waveform = waveformBase64,
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

    /** Best-effort "received" ack; failures are fine — the read receipt later catches the sender up. */
    private fun ackDelivered(conversationId: String, sequence: Long) {
        repositoryScope.launch {
            runCatching { chatApi.markDelivered(conversationId, MarkReadBody(sequence)) }
        }
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

    // --- Phase 2A: group governance ---
    private fun GroupInfoWire.toDomain() = GroupInfo(
        myRole = GroupRole.parse(myRole),
        settings = GroupSettings(
            adminsOnlySend = sendMessages == "admins",
            adminsOnlyAddMembers = addMembers == "admins",
            adminsOnlyEditInfo = editInfo == "admins",
            adminsOnlyPin = pinMessages == "admins",
            slowModeSeconds = slowModeSeconds,
        ),
    )

    private fun InviteWire.toDomain() = GroupInvite(
        id = id,
        code = code,
        expiresAtEpochMillis = expiresAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
        maxUses = maxUses,
        uses = uses,
    )

    private fun who(adminsOnly: Boolean) = if (adminsOnly) "admins" else "all"

    override suspend fun getGroupInfo(conversationId: String): ChatResult<GroupInfo> =
        letterCall { chatApi.getGroupInfo(conversationId).toDomain() }

    override suspend fun updateGroupSettings(conversationId: String, settings: GroupSettings): ChatResult<GroupInfo> = letterCall {
        chatApi.updateGroup(
            conversationId,
            UpdateGroupBody(
                sendMessages = who(settings.adminsOnlySend),
                addMembers = who(settings.adminsOnlyAddMembers),
                editInfo = who(settings.adminsOnlyEditInfo),
                pinMessages = who(settings.adminsOnlyPin),
                slowModeSeconds = settings.slowModeSeconds,
            ),
        ).toDomain()
    }

    override suspend fun translateText(text: String, target: String): ChatResult<String> = letterCall {
        chatApi.translate(com.omidgame.mench.core.network.TranslateBody(text, target)).translatedText
    }

    override suspend fun listSeenBy(conversationId: String, messageServerId: String): ChatResult<List<com.omidgame.mench.feature.chat.domain.SeenBy>> = letterCall {
        chatApi.seenBy(conversationId, messageServerId).map {
            com.omidgame.mench.feature.chat.domain.SeenBy(
                userId = it.userId,
                name = it.displayName ?: it.phoneE164,
                readAtEpochMillis = java.time.Instant.parse(it.readAt).toEpochMilli(),
            )
        }
    }

    override suspend fun listEditHistory(conversationId: String, messageServerId: String): ChatResult<List<com.omidgame.mench.feature.chat.domain.EditVersion>> = letterCall {
        chatApi.editHistory(conversationId, messageServerId).map {
            com.omidgame.mench.feature.chat.domain.EditVersion(
                body = it.body,
                replacedAtEpochMillis = java.time.Instant.parse(it.replacedAt).toEpochMilli(),
            )
        }
    }

    override suspend fun transferOwnership(conversationId: String, userId: String): ChatResult<Unit> = letterCall {
        val response = chatApi.transferOwner(conversationId, com.omidgame.mench.core.network.TransferOwnerBody(userId))
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }

    override suspend fun changeMemberRole(conversationId: String, userId: String, role: GroupRole): ChatResult<Unit> = letterCall {
        val response = chatApi.changeRole(conversationId, userId, ChangeRoleBody(if (role == GroupRole.ADMIN) "admin" else "member"))
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }

    override suspend fun createInvite(conversationId: String, expiresInHours: Int?, maxUses: Int?): ChatResult<GroupInvite> =
        letterCall { chatApi.createInvite(conversationId, CreateInviteBody(expiresInHours, maxUses)).toDomain() }

    override suspend fun listInvites(conversationId: String): ChatResult<List<GroupInvite>> =
        letterCall { chatApi.listInvites(conversationId).map { it.toDomain() } }

    override suspend fun revokeInvite(conversationId: String, inviteId: String): ChatResult<Unit> = letterCall {
        val response = chatApi.revokeInvite(conversationId, inviteId)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }

    override suspend fun previewInvite(code: String): ChatResult<InvitePreview> =
        letterCall { chatApi.previewInvite(code).let { InvitePreview(it.valid, it.problem, it.title, it.memberCount) } }

    override suspend fun joinByInvite(code: String): ChatResult<String> = letterCall { chatApi.joinByInvite(code).id }

    // --- Phase 2A: scheduled messages ---
    private fun ScheduledMessageWire.toDomain() = ScheduledMessage(
        id = id,
        body = body,
        silent = silent,
        sendAtEpochMillis = runCatching { java.time.Instant.parse(sendAt).toEpochMilli() }.getOrDefault(0L),
    )

    override suspend fun scheduleMessage(conversationId: String, body: String, sendAtEpochMillis: Long, silent: Boolean): ChatResult<ScheduledMessage> =
        letterCall {
            // Phase 4C: a scheduled message in a secure chat is encrypted now (never stored as plaintext on the server).
            val wireBody = com.omidgame.mench.core.crypto.E2eeManager.prepareOutgoing(chatApi, conversationId, body)
                ?: throw java.io.IOException("secure chat: partner key unavailable")
            chatApi.scheduleMessage(
                conversationId,
                ScheduleMessageBody(UUID.randomUUID().toString(), wireBody, java.time.Instant.ofEpochMilli(sendAtEpochMillis).toString(), silent),
            ).toDomain()
        }

    override suspend fun listScheduled(conversationId: String): ChatResult<List<ScheduledMessage>> =
        letterCall { chatApi.listScheduled(conversationId).map { it.toDomain() }.sortedBy { it.sendAtEpochMillis } }

    override suspend fun cancelScheduled(id: String): ChatResult<Unit> = letterCall {
        val response = chatApi.cancelScheduled(id)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }

    // --- Phase 2A: polls ---
    private val _pollUpdates = kotlinx.coroutines.flow.MutableSharedFlow<Poll>(replay = 0, extraBufferCapacity = 16)
    override fun observePollUpdates(): Flow<Poll> = _pollUpdates

    private fun PollWire.toDomain() = Poll(
        id = id,
        conversationId = conversationId,
        creatorId = creatorId,
        question = question,
        multiple = multiple,
        closed = closed,
        options = options.map { PollOption(it.id, it.text, it.voterIds) },
    )

    override suspend fun createPoll(conversationId: String, question: String, options: List<String>, multiple: Boolean): ChatResult<Poll> =
        letterCall { chatApi.createPoll(conversationId, CreatePollBody(question, options, multiple)).toDomain() }

    override suspend fun loadPoll(pollId: String): ChatResult<Poll> = letterCall { chatApi.getPoll(pollId).toDomain() }

    override suspend fun votePoll(pollId: String, optionIds: List<String>): ChatResult<Poll> =
        letterCall { chatApi.votePoll(pollId, VotePollBody(optionIds)).toDomain() }

    override suspend fun closePoll(pollId: String): ChatResult<Poll> = letterCall { chatApi.closePoll(pollId).toDomain() }

    override suspend fun markConversationRead(conversationId: String) {
        val highest = withContext(Dispatchers.IO) { messageDao.highestSequence(conversationId) } ?: return
        markRead(conversationId, highest)
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
            // Phase 4C: the server copy of a secure message is ciphertext the target chat could not read,
            // so a secure message is re-sent as fresh text (it will be encrypted again if the target is secure).
            if (com.omidgame.mench.core.crypto.E2eeManager.isSecureChat(sourceConversationId) && source.kind.equals("TEXT", ignoreCase = true)) {
                enqueueText(targetConversationId, source.body.orEmpty(), null, false)
                return@withContext
            }
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

    override suspend fun retryFailedMessage(clientMsgId: String) = withContext(Dispatchers.IO) {
        val entry = outboxDao.getById(clientMsgId) ?: return@withContext
        outboxDao.resetForRetry(clientMsgId, OutboxState.PENDING.name)
        messageDao.updateState(clientMsgId, MessageState.PENDING.name)
        UploadProgress.acknowledgeCancel(clientMsgId)
        outboxSyncScheduler.scheduleNow()
    }

    override suspend fun discardFailedMessage(clientMsgId: String) = withContext(Dispatchers.IO) {
        val entry = outboxDao.getById(clientMsgId)
        if (entry != null) {
            outboxDao.delete(clientMsgId)
            // Free the cached upload copy, if this was an attachment send.
            if (entry.operationType == OutboxOperationType.SEND_ATTACHMENT_MESSAGE) {
                runCatching {
                    moshi.adapter(com.omidgame.mench.core.work.SendAttachmentMessagePayload::class.java)
                        .fromJson(entry.payloadJson)
                }.getOrNull()?.let { payload ->
                    attachmentCache.delete(payload.localContentUri)
                    payload.thumbnailLocalPath?.let { attachmentCache.delete(it) }
                }
            }
        }
        UploadProgress.clear(clientMsgId)
        messageDao.deleteUnsent(clientMsgId)
    }

    override fun cancelUpload(clientMsgId: String) {
        UploadProgress.requestCancel(clientMsgId)
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
        if (reconnectSyncJob?.isActive != true) {
            // Phase 1B delta sync: after every (re)connect, pull only what was missed while the
            // socket was down. syncMessages uses the highest known sequence as its cursor, so a
            // reconnect never re-downloads history, and a first connect is just a cheap no-op.
            reconnectSyncJob = repositoryScope.launch {
                var wasDown = false
                realtimeClient.connectionState.collect { state ->
                    when (state) {
                        is com.omidgame.mench.core.network.realtime.ConnectionState.Connected -> {
                            if (wasDown) {
                                runCatching {
                                    refreshConversations()
                                    conversationDao.getAllOnce().forEach { syncMessages(it.id) }
                                }
                            }
                            wasDown = false
                        }
                        else -> wasDown = true
                    }
                }
            }
        }
    }

    override fun disconnectRealtime() {
        realtimeClient.disconnect()
        eventCollectionJob?.cancel()
        eventCollectionJob = null
        reconnectSyncJob?.cancel()
        reconnectSyncJob = null
    }

    private suspend fun handleRealtimeEvent(event: ServerToClientEvent) {
        when (event) {
            is ServerToClientEvent.MessageCreated -> {
                // Always from another member (the server never echoes
                // message.created back to the sender — see
                // chat.gateway.ts) — this device never has a pre-existing
                // local cache for someone else's attachment.
                messageDao.upsert(event.message.toEntity(existingLocalContentUri = null, existingLocalThumbnailUri = null))
                ackDelivered(event.message.conversationId, event.message.sequence)
                conversationDao.updateLastMessageAt(
                    event.message.conversationId,
                    java.time.Instant.parse(event.message.createdAt).toEpochMilli(),
                )
                // Phase 2A: an archived chat comes back to the main list when a new message arrives, unless it is muted.
                runCatching {
                    val convo = conversationDao.getById(event.message.conversationId)
                    // Settings Phase 2: "keep archived chats archived" turns this auto-unarchive off.
                    if (convo != null && convo.isArchived && !convo.isMuted &&
                        !com.omidgame.mench.feature.chat.presentation.ChatSettingsStore.current.keepArchived
                    ) {
                        conversationDao.updateArchived(convo.id, false)
                        // Best effort: keep the server flag (and the user's other devices) in step.
                        runCatching { chatApi.updateConversationSettings(convo.id, UpdateConversationSettingsBody(isArchived = false)) }
                    }
                }
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
                messageDao.updateReactions(event.messageId, event.reactions.toReactionsJsonWire())
            }
            is ServerToClientEvent.MessageDelivered -> {
                val selfId = selfUserId()
                if (selfId != null && event.userId != selfId) {
                    messageDao.markDeliveredUpTo(
                        event.conversationId,
                        selfId,
                        event.lastDeliveredSequence,
                        MessageState.SENT.name,
                        MessageState.DELIVERED.name,
                    )
                }
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
            is ServerToClientEvent.UsUpdated ->
                conversationDao.updateUsDates(event.conversationId, event.togetherSince, event.nextMeetDate)
            is ServerToClientEvent.PollUpdated -> _pollUpdates.emit(event.poll.toDomain())
            // Phase 4C: observed by DisappearingViewModel straight from RealtimeClient.events; nothing to persist.
            is ServerToClientEvent.DisappearingChanged -> Unit
            is ServerToClientEvent.PinChanged -> conversationDao.updatePinnedMessage(event.conversationId, event.messageId)
            is ServerToClientEvent.QuestionAnswered -> _questionChanges.emit(event.conversationId)
            is ServerToClientEvent.WishlistChanged -> _wishlistChanges.emit(event.conversationId)
            is ServerToClientEvent.MoodChanged -> _moodChanges.emit(event.conversationId)
            is ServerToClientEvent.MemoryChanged -> _memoryChanges.emit(event.conversationId)
            is ServerToClientEvent.CounterChanged -> _counterChanges.emit(event.conversationId)
            is ServerToClientEvent.CanvasChanged -> _canvasChanges.emit(event.conversationId)
            is ServerToClientEvent.StoryChanged -> _storyChanges.emit(event.conversationId)
            is ServerToClientEvent.VaultChanged -> _vaultChanges.emit(event.conversationId)
            is ServerToClientEvent.QuizChanged -> _quizChanges.emit(event.conversationId)
            is ServerToClientEvent.AlbumChanged -> _albumChanges.emit(event.conversationId)
            is ServerToClientEvent.AffectionReceived -> {
                val selfId = selfUserId()
                if (event.from != selfId) _affection.emit(event.conversationId to event.kind)
            }
            is ServerToClientEvent.LetterReceived -> _letterChanges.emit(event.conversationId)
            is ServerToClientEvent.LetterOpened -> _letterChanges.emit(event.conversationId)
            is ServerToClientEvent.MissYou -> {
                val selfId = selfUserId()
                if (event.from != selfId) _missYou.emit(event.conversationId)
            }
            is ServerToClientEvent.Heartbeat -> {
                val selfId = selfUserId()
                if (event.from != selfId) _heartbeats.emit(event.conversationId to event.durationMs)
            }
            is ServerToClientEvent.Together -> {
                val selfId = selfUserId()
                if (event.from != selfId) {
                    _together.emit(
                        event.conversationId to com.omidgame.mench.feature.chat.domain.TogetherSignal(
                            event.kind, event.attachmentId, event.positionMs, event.playing, event.sentAt,
                        ),
                    )
                }
            }
            is ServerToClientEvent.ScreenEffect -> {
                val selfId = selfUserId()
                if (event.from != selfId) _screenEffects.emit(event.conversationId to event.effect)
            }
            is ServerToClientEvent.PresenceChanged -> {
                val lastSeen = event.lastSeenAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                val previous = _presence.value[event.userId]
                _presence.value = _presence.value + (
                    event.userId to UserPresence(
                        online = event.online,
                        // An "online" event carries no lastSeenAt; keep the last one we knew.
                        lastSeenAtEpochMillis = lastSeen ?: previous?.lastSeenAtEpochMillis,
                    )
                    )
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

            // Call signaling events are handled by the calls feature (see
            // CallViewModel), which collects realtimeClient.events on its
            // own — nothing for the chat repository to do with them.
            is ServerToClientEvent.CallIncoming,
            is ServerToClientEvent.CallAccepted,
            is ServerToClientEvent.CallDeclined,
            is ServerToClientEvent.CallEnded,
            is ServerToClientEvent.CallOffer,
            is ServerToClientEvent.CallAnswer,
            is ServerToClientEvent.CallIceCandidate,
            is ServerToClientEvent.CallHangup,
            is ServerToClientEvent.CallChat,
            is ServerToClientEvent.CallReaction,
            -> Unit
        }
    }

    private suspend fun selfUserId(): String? =
        cachedSelfUserId ?: userDao.getSelfOnce()?.id?.also { cachedSelfUserId = it }
}
