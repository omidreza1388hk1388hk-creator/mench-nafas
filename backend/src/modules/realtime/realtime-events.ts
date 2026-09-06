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
  hasThumbnail: boolean;
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

export type ServerToClientEvent =
  | { type: 'message.created'; message: MessageDto }
  /** Body edit only — deletion has its own, cheaper event below rather than re-sending the whole (now-empty) message. */
  | { type: 'message.updated'; message: MessageDto }
  | { type: 'message.deleted'; conversationId: string; messageId: string; deletedAt: string }
  | { type: 'reaction.updated'; conversationId: string; messageId: string; reactions: ReactionSummary[] }
  | { type: 'message.read'; conversationId: string; userId: string; lastReadSequence: number }
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
  | { type: 'call.hangup'; callId: string; from: string };

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
  | { type: 'call.hangup'; callId: string };
