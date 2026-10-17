/**
 * In-call extras (Phase 2B): a short text chat and emoji reactions, relayed over the WebSocket to
 * the other participant only (never persisted; same trust model as the SDP/ICE relay).
 * Validation lives here, pure, so it is unit-tested without a socket.
 */
export const MAX_CALL_CHAT_LENGTH = 500;

/** The emoji the in-call reaction bar offers. Anything else is dropped so a client can't relay arbitrary text as a "reaction". */
export const CALL_REACTIONS = ['❤️', '😂', '😮', '👍', '🔥', '😘', '🥰', '👏'] as const;

export function sanitizeCallChatText(raw: unknown): string | null {
  if (typeof raw !== 'string') return null;
  const text = raw.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '').trim();
  if (text.length === 0) return null;
  return text.slice(0, MAX_CALL_CHAT_LENGTH);
}

export function isAllowedCallReaction(raw: unknown): raw is (typeof CALL_REACTIONS)[number] {
  return typeof raw === 'string' && (CALL_REACTIONS as readonly string[]).includes(raw);
}
