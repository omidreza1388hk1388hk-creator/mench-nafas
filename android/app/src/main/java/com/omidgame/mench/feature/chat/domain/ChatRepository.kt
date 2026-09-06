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
    fun observeMessages(conversationId: String): Flow<List<Message>>

    suspend fun startDirectConversation(targetPhoneE164: String): ChatResult<Conversation>
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
    suspend fun sendMessage(conversationId: String, body: String)

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
    )

    suspend fun markRead(conversationId: String, upToSequence: Long)

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
