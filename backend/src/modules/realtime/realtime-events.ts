/**
 * Wire format for every event sent over the WebSocket, in both directions.
 * Kept as a flat discriminated union (single `type` field) rather than
 * nested envelopes, so the Android client's JSON parsing stays simple:
 * decode once, switch on `type`.
 */

import { ConversationDto } from '../conversations/conversation.dto';

export interface MessageAttachmentSummary {
  id: string;
  kind: string; // 'image' | 'file' | 'audio'
  mimeType: string;
  originalFilename: string;
  sizeBytes: number;
  widthPx: number | null;
  heightPx: number | null;
  durationMs: number | null;
  /** Audio only — base64 amplitude samples, null when absent (older clients). */
  waveform: string | null;
  hasThumbnail: boolean;
  /** Optional so older fixtures/clients keep working; see AttachmentDto.blurHash. */
  blurHash?: string | null;
}

/**
 * One row per distinct emoji on a message, with every user who currently
 * has that emoji active (not just a count) — the client needs the full
 * userId list to render "reactedByMe" for the current user without a
 * second lookup, and to eventually show "who reacted" on long-press.
 */
export interface ReactionSummary {
  emoji: string;
  userIds: string[];
}

export interface MessageDto {
  id: string;
  conversationId: string;
  senderId: string;
  clientMsgId: string;
  kind: string; // 'text' | 'image' | 'file'
  body: string | null;
  /**
   * The full attachment summary, embedded directly — not just an id.
   * A client rendering a message list needs mimeType/dimensions/filename
   * immediately to lay out the bubble; fetching those per-message after
   * the fact would be an N+1 round trip for every image in a
   * conversation's history. See MessagesRepository.listForConversation's
   * JOIN.
   */
  attachment: MessageAttachmentSummary | null;
  sequence: number;
  createdAt: string; // ISO 8601
  editedAt: string | null;
  deletedAt: string | null;
  /** Set only on a message created via MessagesService.forward — points at the original message it was forwarded from. */
  forwardedFromMessageId: string | null;
  /** Set when this message was sent as a reply — points at the quoted message, within the same conversation (validated server-side in MessagesService.send). */
  replyToMessageId: string | null;
  reactions: ReactionSummary[];
}

/**
 * Phase 6 (calls) wire types. Defined locally here — not imported from
 * the calls module — matching MessageDto above's precedent: every
 * realtime payload shape is self-contained in this file so the Android
 * side has exactly one place (RealtimeEvents.kt) to mirror, independent
 * of any particular module's internal DTOs.
 *
 * No SDP or ICE candidate data is ever persisted server-side — these are
 * pure pass-through relays, authorized per-frame by ChatGateway against
 * CallsRepository.isParticipant, and are the only realtime events in this
 * file that carry payloads the server itself never inspects the contents
 * of (a raw SDP string, an ICE candidate string).
 */
export interface CallIncomingSummary {
  id: string;
  conversationId: string;
  callerId: string;
  callType: string; // 'voice' | 'video'
  callerDisplayName: string | null;
  callerPhoneE164: string;
}

/** Phase 2A — a poll (question, options and who voted for what). Sent whole on every change; the client replaces its copy. */
export interface PollEventDto {
  id: string;
  conversationId: string;
  creatorId: string;
  question: string;
  multiple: boolean;
  closed: boolean;
  createdAt: string;
  options: { id: string; text: string; voterIds: string[] }[];
}

export type ServerToClientEvent =
  | { type: 'message.created'; message: MessageDto }
  /** Phase 2A — someone voted in / closed a poll in this conversation. */
  | { type: 'poll.updated'; conversationId: string; poll: PollEventDto }
  /** Body edit only — deletion has its own, cheaper event below rather than re-sending the whole (now-empty) message. */
  | { type: 'message.updated'; message: MessageDto }
  | { type: 'message.deleted'; conversationId: string; messageId: string; deletedAt: string }
  | { type: 'reaction.updated'; conversationId: string; messageId: string; reactions: ReactionSummary[] }
  | { type: 'message.delivered'; conversationId: string; userId: string; lastDeliveredSequence: number }
  | { type: 'message.read'; conversationId: string; userId: string; lastReadSequence: number }
  /** Phase 9 — a shared 'us' date changed; sent to every member so both phones show the same counter. */
  | { type: 'us.updated'; conversationId: string; togetherSince: string | null; nextMeetDate: string | null }
  /** Phase 11 — the pinned message of a conversation changed (messageId null = unpinned). */
  | { type: 'pin.changed'; conversationId: string; messageId: string | null }
  /** Phase 4C — the disappearing-messages timer of a conversation changed (seconds null = off). */
  | { type: 'conversation.disappearing_changed'; conversationId: string; seconds: number | null; changedBy: string }
  /** Phase 11 — the partner answered today's question. */
  | { type: 'question.answered'; conversationId: string }
  /** Phase 11 — the shared wishlist changed. */
  | { type: 'wishlist.changed'; conversationId: string }
  /** Phase 9 — the partner set today's mood. */
  | { type: 'mood.changed'; conversationId: string; userId: string; emoji: string; text: string | null; setAt: string }
  /** Phase 9 — the shared memories list changed (added/removed by the partner); reload it. */
  | { type: 'memory.changed'; conversationId: string }
  /** Phase 9 — a hug or kiss from the partner; never persisted, the client plays an animation + vibration. */
  | { type: 'affection'; conversationId: string; from: string; kind: 'hug' | 'kiss' }
  /** Phase 9 — a new "open when…" letter was written to the recipient (body stays sealed until they open it). */
  | { type: 'letter.received'; conversationId: string; letterId: string }
  /** Phase 9 — the recipient opened a letter you wrote. */
  | { type: 'letter.opened'; conversationId: string; letterId: string }
  /** Phase 9 — the 'I miss you' tap. Never persisted; the receiving client plays a heart animation + heartbeat vibration. */
  | { type: 'miss_you'; conversationId: string; from: string }
  /** Phase 3A — a live heartbeat from the partner (press-and-hold heart). Never persisted and never pushed: it only means something while the receiver is online. The client vibrates in rhythm and plays a full-screen heartbeat for durationMs. */
  /** Phase 3A — the shared counters list changed (added, bumped or removed by the partner); reload it. */
  | { type: 'counter.changed'; conversationId: string }
  /** Phase 3A — the shared drawing canvas changed (strokes added or cleared by the partner); reload it. */
  | { type: 'canvas.changed'; conversationId: string }
  /** Phase 3B — stories in this conversation changed (new, deleted, or your story was seen); reload them. */
  | { type: 'story.changed'; conversationId: string }
  /** Phase 3 — the couple's encrypted vault changed (a note was added or deleted by the partner); reload it. */
  | { type: 'vault.changed'; conversationId: string }
  /** Phase 3A — a quiz was added or deleted, or your quiz was answered; reload the list. */
  | { type: 'quiz.changed'; conversationId: string }
  /** Phase 3 — the shared album changed (photo added, reacted to, or deleted by the partner); reload it. */
  | { type: 'album.changed'; conversationId: string }
  | { type: 'heartbeat'; conversationId: string; from: string; durationMs: number }
  /** Phase 3 — Together mode: the partner started/stopped a shared playback or moved/paused/resumed it. Relay only. */
  | {
      type: 'together';
      conversationId: string;
      from: string;
      kind: 'start' | 'sync' | 'stop';
      attachmentId: string | null;
      positionMs: number;
      playing: boolean;
      sentAt: number;
    }
  /** Phase 3A — a full-screen effect the sender picked by hand (long-press send). Ephemeral like miss_you: words that trigger an effect are detected on the receiving phone from the message text itself. */
  | { type: 'screen_effect'; conversationId: string; from: string; effect: string }
  /** Online/offline transition of someone who shares a conversation with the recipient; also sent once per co-member as a snapshot right after a socket connects. lastSeenAt is null if never seen. */
  | { type: 'presence.changed'; userId: string; online: boolean; lastSeenAt: string | null }
  | { type: 'typing.started'; conversationId: string; userId: string }
  | { type: 'typing.stopped'; conversationId: string; userId: string }
  /**
   * Sent to a member who is newly added to a group — carries the full
   * ConversationDto so their client can insert a row into its local
   * (Room) cache immediately, without a follow-up REST round trip just
   * to discover a conversation it was just told exists. Not sent to
   * members who were already in the conversation (they get
   * group.member_added below instead, which is cheaper and is all they
   * need since they already have the conversation cached).
   */
  | { type: 'conversation.added'; conversation: ConversationDto }
  /** Broadcast to every member remaining in the group AFTER the add, including the new member. */
  | { type: 'group.member_added'; conversationId: string; userId: string; addedBy: string }
  /** Broadcast to every member who was in the group BEFORE the removal, including the removed user themselves (so their own client can drop the conversation locally). */
  | { type: 'group.member_removed'; conversationId: string; userId: string; removedBy: string }
  /** Same before-removal broadcast rule as group.member_removed, for the self-removal ("leave") path. */
  | { type: 'group.member_left'; conversationId: string; userId: string }
  /** Broadcast to every current member after the rename commits. */
  | { type: 'group.renamed'; conversationId: string; title: string; renamedBy: string }
  | { type: 'call.incoming'; call: CallIncomingSummary }
  | { type: 'call.accepted'; callId: string; by: string }
  | { type: 'call.declined'; callId: string; by: string }
  | { type: 'call.ended'; callId: string; by: string; status: string; reason: string | null }
  /** Relayed from the other participant's matching call.offer/answer/ice-candidate ClientToServerEvent below — `from` lets the receiving client ignore stale frames for a call it already tore down. */
  | { type: 'call.offer'; callId: string; from: string; sdp: string }
  | { type: 'call.answer'; callId: string; from: string; sdp: string }
  | {
      type: 'call.ice-candidate';
      callId: string;
      from: string;
      candidate: string;
      sdpMid: string | null;
      sdpMLineIndex: number | null;
    }
  /** Fast-path relay only — REST POST /calls/:id/end (see CallsController) remains the authoritative state transition; this just lets the other client's UI tear down immediately instead of waiting on that request's own broadcast. */
  | { type: 'call.hangup'; callId: string; from: string }
  /** Phase 2B — in-call text chat line from the other participant (relayed, never stored). */
  | { type: 'call.chat'; callId: string; from: string; text: string; sentAt: string }
  /** Phase 2B — an emoji reaction during the call. */
  | { type: 'call.reaction'; callId: string; from: string; emoji: string };

export type ClientToServerEvent =
  | { type: 'typing.started'; conversationId: string }
  | { type: 'typing.stopped'; conversationId: string }
  | { type: 'call.offer'; callId: string; sdp: string }
  | { type: 'call.answer'; callId: string; sdp: string }
  | {
      type: 'call.ice-candidate';
      callId: string;
      candidate: string;
      sdpMid: string | null;
      sdpMLineIndex: number | null;
    }
  | { type: 'call.hangup'; callId: string }
  | { type: 'call.chat'; callId: string; text: string }
  | { type: 'call.reaction'; callId: string; emoji: string };
