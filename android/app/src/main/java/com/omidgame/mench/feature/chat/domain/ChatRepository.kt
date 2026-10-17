package com.omidgame.mench.feature.chat.domain

import kotlinx.coroutines.flow.Flow

/**
 * Everything above this interface (use cases, ViewModels) only ever reads
 * conversations/messages via the Flow-returning observe* methods, which
 * are backed by Room — never directly by network response. This is what
 * makes the UI offline-first and local-first: a send shows up in the
 * message list the instant it's written locally (PENDING state), before
 * any network round trip, and realtime/sync updates are applied by
 * writing to Room, which the same Flow then re-emits. The UI never
 * branches on "am I online" — it just renders whatever Room currently has.
 */
interface ChatRepository {
    /** The signed-in user's own id — used by screens like Group Info to tell "leave" (self) apart from "remove" (someone else) and to compute owner-only UI, without duplicating the userDao/TokenStore lookup this repository already does internally for isOwn on messages. */
    suspend fun currentUserId(): String?

    fun observeConversations(): Flow<List<Conversation>>

    /** Latest message + unread count per conversation id. Default empty so simple fakes need not implement it. */
    fun observeConversationPreviews(): Flow<Map<String, ConversationPreview>> =
        kotlinx.coroutines.flow.flowOf(emptyMap())
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /** Newest [limit] messages only (Phase 1B windowed history). Default = everything, so fakes need no change. */
    fun observeMessageWindow(conversationId: String, limit: Int): Flow<List<Message>> = observeMessages(conversationId)

    suspend fun startDirectConversation(targetPhoneE164: String): ChatResult<Conversation>

    /** The private Saved messages chat (created on first use). */
    suspend fun openSavedMessages(): ChatResult<Conversation> = ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** Starts (or reopens) a 1:1 chat from a user id, for username/invite/QR results where no phone number is known. Default keeps simple fakes compiling. */
    suspend fun startDirectConversationWithUser(userId: String): ChatResult<Conversation> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)
    suspend fun refreshConversations(): ChatResult<Unit>
    suspend fun syncMessages(conversationId: String): ChatResult<Unit>

    /** memberPhoneNumbers is resolved to user ids server-side one at a time (see ChatRepositoryImpl) — a phone that doesn't resolve to a MENCH user fails the whole call with USER_NOT_FOUND rather than silently dropping that member. */
    suspend fun createGroup(title: String, memberPhoneNumbers: List<String>): ChatResult<Conversation>

    /** Not cached in Room — always a live fetch, since membership/roles only matter while a Group Info screen is actually open (see GroupInfoViewModel). */
    suspend fun listMembers(conversationId: String): ChatResult<List<ConversationMember>>

    suspend fun addMembers(conversationId: String, memberPhoneNumbers: List<String>): ChatResult<Unit>

    /** userId == the signed-in user's own id is "leave group" — same self-vs-other split as the backend's removeMember. */
    suspend fun removeMember(conversationId: String, userId: String): ChatResult<Unit>

    suspend fun renameGroup(conversationId: String, title: String): ChatResult<Unit>

    /** Writes a PENDING message to Room immediately and queues a durable Outbox entry — returns as soon as both are written, not when the send actually completes. */
    suspend fun sendMessage(conversationId: String, body: String, replyToMessageId: String? = null, silent: Boolean = false)

    /** Like [sendMessage] but returns the local client id, so a live location can find the message to keep editing. */
    suspend fun startLiveLocation(conversationId: String, body: String): String? = null

    /** The server id of a locally sent message once the send completed, else null. */
    suspend fun serverIdOf(clientMsgId: String): String? = null

    /**
     * Same optimistic-local-first contract as sendMessage, but for an
     * attachment: copies the picked content into app-private storage
     * immediately (so it survives the caller's Uri permission being
     * revoked), writes a PENDING message with that local copy referenced,
     * and queues a durable Outbox entry that uploads + sends once network
     * is available — across app restarts, process death, and device
     * reboot, same as a text message's Outbox entry.
     *
     * thumbnailLocalUri/widthPx/heightPx are video-only — see
     * VideoMetadataExtractor, which produces all three client-side so the
     * server never needs to run video processing of its own.
     */
    suspend fun sendAttachment(
        conversationId: String,
        contentUri: String,
        mimeType: String,
        originalFilename: String,
        caption: String?,
        durationMs: Long? = null,
        thumbnailLocalUri: String? = null,
        widthPx: Int? = null,
        heightPx: Int? = null,
        /** Phase 2A — voice messages: ~48 amplitude samples (0..255) measured while recording. */
        waveform: List<Int>? = null,
    )

    /** Phase 1B: puts a failed send back in the Outbox with a fresh retry budget. Default no-op for simple fakes. */
    suspend fun retryFailedMessage(clientMsgId: String) {}

    /** Phase 1B: removes a send that failed and was never accepted by the server. Default no-op for simple fakes. */
    suspend fun discardFailedMessage(clientMsgId: String) {}

    /** Phase 1B: asks an in-flight attachment upload to stop; the bubble then shows as FAILED (retry / delete). */
    fun cancelUpload(clientMsgId: String) {}

    suspend fun markRead(conversationId: String, upToSequence: Long)

    // --- Phase 2A: group governance (roles, permissions, slow mode, invite links) ---
    suspend fun getGroupInfo(conversationId: String): ChatResult<GroupInfo> = ChatResult.Failure(ChatFailureReason.UNKNOWN)
    suspend fun updateGroupSettings(conversationId: String, settings: GroupSettings): ChatResult<GroupInfo> = ChatResult.Failure(ChatFailureReason.UNKNOWN)
    /** Translates [text] into [target] ("fa", "en" or "ar") through the backend proxy. */
    suspend fun translateText(text: String, target: String): ChatResult<String> = ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** Who has read my message (server id) in this group. */
    suspend fun listSeenBy(conversationId: String, messageServerId: String): ChatResult<List<SeenBy>> = ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** Earlier versions of an edited message (oldest first). */
    suspend fun listEditHistory(conversationId: String, messageServerId: String): ChatResult<List<EditVersion>> = ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** Owner only: hands the group to another member (the previous owner becomes an admin). */
    suspend fun transferOwnership(conversationId: String, userId: String): ChatResult<Unit> = ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun changeMemberRole(conversationId: String, userId: String, role: GroupRole): ChatResult<Unit> = ChatResult.Failure(ChatFailureReason.UNKNOWN)
    suspend fun createInvite(conversationId: String, expiresInHours: Int?, maxUses: Int?): ChatResult<GroupInvite> = ChatResult.Failure(ChatFailureReason.UNKNOWN)
    suspend fun listInvites(conversationId: String): ChatResult<List<GroupInvite>> = ChatResult.Success(emptyList())
    suspend fun revokeInvite(conversationId: String, inviteId: String): ChatResult<Unit> = ChatResult.Failure(ChatFailureReason.UNKNOWN)
    suspend fun previewInvite(code: String): ChatResult<InvitePreview> = ChatResult.Failure(ChatFailureReason.UNKNOWN)
    /** Joins the group behind [code]; returns the conversation id to open. */
    suspend fun joinByInvite(code: String): ChatResult<String> = ChatResult.Failure(ChatFailureReason.UNKNOWN)

    // --- Phase 2A: scheduled messages (server-side; the server sends them at the chosen time) ---
    suspend fun scheduleMessage(conversationId: String, body: String, sendAtEpochMillis: Long, silent: Boolean): ChatResult<ScheduledMessage> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)
    suspend fun listScheduled(conversationId: String): ChatResult<List<ScheduledMessage>> = ChatResult.Success(emptyList())
    suspend fun cancelScheduled(id: String): ChatResult<Unit> = ChatResult.Failure(ChatFailureReason.UNKNOWN)

    // --- Phase 2A: polls (live REST; state is not cached in Room) ---
    suspend fun createPoll(conversationId: String, question: String, options: List<String>, multiple: Boolean): ChatResult<Poll> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)
    suspend fun loadPoll(pollId: String): ChatResult<Poll> = ChatResult.Failure(ChatFailureReason.UNKNOWN)
    suspend fun votePoll(pollId: String, optionIds: List<String>): ChatResult<Poll> = ChatResult.Failure(ChatFailureReason.UNKNOWN)
    suspend fun closePoll(pollId: String): ChatResult<Poll> = ChatResult.Failure(ChatFailureReason.UNKNOWN)
    /** Polls changed by someone else (realtime). */
    fun observePollUpdates(): Flow<Poll> = kotlinx.coroutines.flow.emptyFlow()

    /** Phase 2A (chat-list swipe / menu): marks everything this phone has for the conversation as read. Default no-op for simple fakes. */
    suspend fun markConversationRead(conversationId: String) {}

    /** Optimistically updates the local copy and queues a durable Outbox entry — same contract as sendMessage. Only ever called for a message that already has a serverId (see MessageDao's Phase 4 queries). */
    suspend fun editMessage(conversationId: String, serverId: String, body: String)

    suspend fun deleteMessage(conversationId: String, serverId: String)

    /** Setting a reaction when the user already has a different one active on this message replaces it — never stacks — matching the server's one-reaction-per-user-per-message model. */
    suspend fun setReaction(conversationId: String, serverId: String, emoji: String)

    suspend fun clearReaction(conversationId: String, serverId: String)

    /** Only text messages can be forwarded today — see MessagesService.forward on the backend for why. */
    suspend fun forwardMessage(sourceConversationId: String, sourceServerId: String, targetConversationId: String)

    /** Conversation ids where the other party is currently typing. No persistence, no timeout on a lost "stopped" event — a transient, best-effort signal, not a durable one. */
    fun observeTypingConversationIds(): Flow<Set<String>>

    /** Phase 9. Either person sets the shared dates (ISO yyyy-MM-dd); null leaves a field unchanged. */
    suspend fun setUsDates(conversationId: String, togetherSince: String?, nextMeetDate: String?): ChatResult<Unit>

    /** Phase 9. Today's mood: load both, set mine, and a signal (conversationId) when the partner changes theirs. */
    suspend fun getMoods(conversationId: String): ChatResult<Moods>

    suspend fun setMood(conversationId: String, emoji: String, text: String?): ChatResult<Unit>

    fun observeMoodChanges(): Flow<String>

    /** Phase 11. Server-side link preview (null when nothing to show or on failure). */
    suspend fun linkPreview(url: String): LinkPreview?

    /** Phase 11. Question of the day, wishlist and pinned message. */
    suspend fun getQuestion(conversationId: String): ChatResult<Question>

    suspend fun answerQuestion(conversationId: String, answer: String): ChatResult<Unit>

    fun observeQuestionChanges(): Flow<String>

    suspend fun listWishes(conversationId: String): ChatResult<List<Wish>>

    suspend fun createWish(conversationId: String, title: String): ChatResult<Wish>

    suspend fun setWishDone(conversationId: String, wishId: String, done: Boolean): ChatResult<Wish>

    suspend fun deleteWish(conversationId: String, wishId: String): ChatResult<Unit>

    fun observeWishlistChanges(): Flow<String>

    /** [serverMessageId] = null unpins. */
    suspend fun setPinnedMessage(conversationId: String, serverMessageId: String?): ChatResult<Unit>

    /** Phase 9. Shared memories timeline (fetched live, newest memory-date first). A photo is uploaded first, then referenced by the memory. */
    suspend fun listMemories(conversationId: String): ChatResult<List<Memory>>

    suspend fun createMemory(
        conversationId: String,
        title: String,
        note: String?,
        memoryDateIso: String,
        photoUri: String?,
        photoMimeType: String?,
        photoFilename: String?,
    ): ChatResult<Memory>

    suspend fun deleteMemory(conversationId: String, memoryId: String): ChatResult<Unit>

    fun observeMemoryChanges(): Flow<String>

    /** Phase 3A. Shared counters (fetched live, oldest first). Defaults keep test fakes compiling. */
    suspend fun listCounters(conversationId: String): ChatResult<List<Counter>> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun createCounter(conversationId: String, title: String, emoji: String?, kind: String, anchorDateIso: String?, theme: Int = 0): ChatResult<Counter> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** [delta] is +1 or -1; either person may bump a tally. */
    suspend fun bumpCounter(conversationId: String, counterId: String, delta: Int): ChatResult<Counter> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun deleteCounter(conversationId: String, counterId: String): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    fun observeCounterChanges(): Flow<String> = kotlinx.coroutines.flow.emptyFlow()

    /** Phase 3A. The shared drawing canvas: read it, append strokes (atomic on the server), clear it. */
    suspend fun getCanvas(conversationId: String): ChatResult<List<CanvasStroke>> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun appendCanvasStrokes(conversationId: String, strokes: List<CanvasStroke>): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun clearCanvas(conversationId: String): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    fun observeCanvasChanges(): Flow<String> = kotlinx.coroutines.flow.emptyFlow()

    /** Phase 3B. 24-hour text stories of a conversation. */
    suspend fun listStories(conversationId: String): ChatResult<List<Story>> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun createStory(conversationId: String, body: String, emoji: String?, palette: Int, photo: PickedPhoto? = null): ChatResult<Story> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun viewStory(conversationId: String, storyId: String): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun deleteStory(conversationId: String, storyId: String): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    fun observeStoryChanges(): Flow<String> = kotlinx.coroutines.flow.emptyFlow()

    /** Phase 3B. Ids of conversations that have stories you have not seen (drives the rings on the chat list). */
    suspend fun unseenStoryConversations(): ChatResult<Set<String>> = ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** Phase 3. The couple's encrypted vault (opaque blobs). */
    suspend fun listVault(conversationId: String): ChatResult<List<VaultItem>> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun createVaultItem(conversationId: String, blob: String): ChatResult<VaultItem> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun deleteVaultItem(conversationId: String, itemId: String): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    fun observeVaultChanges(): Flow<String> = kotlinx.coroutines.flow.emptyFlow()

    /** Phase 3A. "How well do you know me" quizzes. */
    suspend fun listQuizzes(conversationId: String): ChatResult<List<QuizSummary>> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun createQuiz(conversationId: String, title: String, questions: List<QuizQuestion>): ChatResult<QuizSummary> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun getQuiz(conversationId: String, quizId: String): ChatResult<QuizDetail> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun answerQuiz(conversationId: String, quizId: String, answers: List<Int>): ChatResult<QuizResult> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun deleteQuiz(conversationId: String, quizId: String): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    fun observeQuizChanges(): Flow<String> = kotlinx.coroutines.flow.emptyFlow()

    /** Phase 3. The shared photo album. */
    suspend fun listAlbum(conversationId: String): ChatResult<List<AlbumItem>> = ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun addAlbumPhoto(conversationId: String, photo: PickedPhoto, caption: String?): ChatResult<AlbumItem> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** [emoji] null removes your reaction. */
    suspend fun reactAlbumItem(conversationId: String, itemId: String, emoji: String?): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    suspend fun deleteAlbumItem(conversationId: String, itemId: String): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    fun observeAlbumChanges(): Flow<String> = kotlinx.coroutines.flow.emptyFlow()

    /** Phase 3A. Statistics of this conversation's text messages in [year], computed on the phone. */
    suspend fun loadYearMessageStats(conversationId: String, year: Int): YearMessageStats = YearMessageStats.EMPTY

    /** Phase 9. Sends a hug or a kiss ([kind] = "hug" | "kiss"). */
    suspend fun sendAffection(conversationId: String, kind: String): ChatResult<Unit>

    /** Phase 9. Emits (conversationId, kind) when the partner sends a hug/kiss while the socket is live. */
    fun observeAffection(): Flow<Pair<String, String>>

    /** Phase 9. "Open when…" letters — fetched live, not cached (they're few, and bodies must never sit sealed-then-stale on disk). */
    suspend fun listLetters(conversationId: String): ChatResult<List<Letter>>

    suspend fun createLetter(conversationId: String, title: String, body: String, unlockAtIso: String?, photo: PickedPhoto? = null): ChatResult<Letter>

    suspend fun openLetter(conversationId: String, letterId: String): ChatResult<Letter>

    /** Phase 9. Emits the conversationId whenever a letter was received or opened (a cue to reload the list). */
    fun observeLetterChanges(): Flow<String>

    /** Phase 9. The "I miss you" tap. */
    suspend fun sendMissYou(conversationId: String): ChatResult<Unit>

    /** Phase 9. Emits the conversationId each time the partner taps "I miss you" while the socket is live. */
    fun observeMissYou(): Flow<String>

    /** Phase 3A. Sends a live heartbeat of [durationMs]; Success(true) means the partner was online to feel it. Default keeps test fakes compiling. */
    suspend fun sendHeartbeat(conversationId: String, durationMs: Long): ChatResult<Boolean> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** Phase 3A. Emits (conversationId, durationMs) each time the partner sends a live heartbeat. */
    fun observeHeartbeats(): Flow<Pair<String, Long>> = kotlinx.coroutines.flow.emptyFlow()

    /** Phase 3A. Sends a hand-picked full-screen effect ([effect] is MessageEffect.wire) to the other members; live only. */
    suspend fun sendScreenEffect(conversationId: String, effect: String): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** Phase 3A. Emits (conversationId, effect wire name) each time another member sends a hand-picked effect. */
    fun observeScreenEffects(): Flow<Pair<String, String>> = kotlinx.coroutines.flow.emptyFlow()

    /** Phase 4C: turn end-to-end encryption on/off for a 1:1 chat. Fails if the partner has no published key. */
    suspend fun setSecureChat(conversationId: String, peerUserId: String?, enabled: Boolean): Boolean = false

    /** Phase 3: Together mode. Live events from the partner: (conversationId, signal). */
    fun observeTogether(): Flow<Pair<String, TogetherSignal>> = kotlinx.coroutines.flow.emptyFlow()

    suspend fun sendTogether(conversationId: String, kind: String, attachmentId: String?, positionMs: Long, playing: Boolean): ChatResult<Unit> =
        ChatResult.Failure(ChatFailureReason.UNKNOWN)

    /** Phase 8. Per-user mute (no push for this chat) and pin (sorted to the top). Server first, then the local cache. */
    suspend fun setConversationMuted(conversationId: String, muted: Boolean): ChatResult<Unit>

    suspend fun setConversationPinned(conversationId: String, pinned: Boolean): ChatResult<Unit>

    /** Phase 10. Per-user archive: hides the chat from the main list without deleting it. */
    suspend fun setConversationArchived(conversationId: String, archived: Boolean): ChatResult<Unit>

    /** Phase 8. userId -> latest known presence, fed by realtime presence.changed events. In-memory only: the server re-sends a snapshot on every socket connect. */
    fun observePresence(): Flow<Map<String, UserPresence>>

    /**
     * Phase 6. Membership/title changes to a group that arrived over the
     * realtime socket — for GroupInfoViewModel to react to while its
     * screen is open, without polling. Conversation-list-level effects of
     * these same events (title changes, a new conversation appearing) are
     * already handled by writing straight to Room (see
     * ChatRepositoryImpl.handleRealtimeEvent) and don't need this flow —
     * observeConversations() picks those up on its own. This flow exists
     * only for the one thing Room-observation can't cover: telling an
     * already-open Group Info screen "reload, something about THIS group
     * just changed" (or "you're not in this group anymore, navigate out").
     */
    fun observeGroupChanges(): Flow<GroupChangeSignal>

    fun connectRealtime()
    fun disconnectRealtime()
}

sealed interface GroupChangeSignal {
    val conversationId: String

    data class MembershipChanged(override val conversationId: String) : GroupChangeSignal
    data class Renamed(override val conversationId: String, val title: String) : GroupChangeSignal
    /** The signed-in user themselves was removed from, or left, this group — GroupInfoViewModel should navigate out rather than reload. */
    data class SelfRemoved(override val conversationId: String) : GroupChangeSignal
}
