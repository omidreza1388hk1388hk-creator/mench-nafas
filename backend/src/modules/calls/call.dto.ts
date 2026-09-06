import { CallHistoryRow, CallRow } from './calls.repository';

export interface IceServerDto {
  urls: string[];
  username: string | null;
  credential: string | null;
}

/**
 * Returned on initiate/accept/get — carries the ICE server list a client
 * needs right now to actually establish the PeerConnection. Deliberately
 * NOT included on CallHistorySummaryDto below: history entries are read
 * long after the call ended, when TURN credentials (often short-lived)
 * would be stale and pointless to hand out anyway.
 */
export interface CallSessionDto {
  id: string;
  conversationId: string;
  callerId: string;
  calleeId: string;
  callType: string;
  status: string;
  startedAt: string;
  answeredAt: string | null;
  endedAt: string | null;
  endReason: string | null;
  iceServers: IceServerDto[];
}

export interface CallHistorySummaryDto {
  id: string;
  conversationId: string;
  callType: string;
  status: string;
  startedAt: string;
  answeredAt: string | null;
  endedAt: string | null;
  /** Null unless both answeredAt and endedAt are set — a ringing/missed/declined call has no meaningful duration. */
  durationSeconds: number | null;
  wasOutgoing: boolean;
  otherUserId: string;
  otherUserPhoneE164: string;
  otherUserDisplayName: string | null;
}

export function toCallSessionDto(row: CallRow, iceServers: IceServerDto[]): CallSessionDto {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    callerId: row.caller_id,
    calleeId: row.callee_id,
    callType: row.call_type,
    status: row.status,
    startedAt: row.started_at.toISOString(),
    answeredAt: row.answered_at ? row.answered_at.toISOString() : null,
    endedAt: row.ended_at ? row.ended_at.toISOString() : null,
    endReason: row.end_reason,
    iceServers,
  };
}

export function toCallHistorySummaryDto(row: CallHistoryRow, requestingUserId: string): CallHistorySummaryDto {
  const durationSeconds =
    row.answered_at && row.ended_at
      ? Math.max(0, Math.round((row.ended_at.getTime() - row.answered_at.getTime()) / 1000))
      : null;

  return {
    id: row.id,
    conversationId: row.conversation_id,
    callType: row.call_type,
    status: row.status,
    startedAt: row.started_at.toISOString(),
    answeredAt: row.answered_at ? row.answered_at.toISOString() : null,
    endedAt: row.ended_at ? row.ended_at.toISOString() : null,
    durationSeconds,
    wasOutgoing: row.caller_id === requestingUserId,
    otherUserId: row.other_user_id,
    otherUserPhoneE164: row.other_phone_e164,
    otherUserDisplayName: row.other_display_name,
  };
}
