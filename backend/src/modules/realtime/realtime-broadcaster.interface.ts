import { ServerToClientEvent } from './realtime-events';

export const REALTIME_BROADCASTER = 'REALTIME_BROADCASTER';

/**
 * Boundary between "something happened" (a message was sent, a read
 * cursor moved) and "tell the right connected clients about it". Services
 * outside this module depend only on this interface, never on ChatGateway
 * or the 'ws' library directly — matches the SmsProvider pattern from
 * Phase 1's auth module.
 */
export interface RealtimeBroadcaster {
  broadcastToUsers(userIds: string[], event: ServerToClientEvent): void;

  /**
   * True if this user has at least one open, authenticated WebSocket
   * connection right now. Used by NotificationsService (Phase 6) to skip
   * sending a push to a device that already got the event live over the
   * socket — a push on top of that would just be a redundant duplicate
   * notification for a foreground user. Deliberately per-user, not
   * per-device: a user with one foreground device and one backgrounded
   * device should still get a push on the backgrounded one, but this
   * coarse-grained check can't tell them apart, so it errs toward "don't
   * double-notify" rather than "never miss a device" — acceptable for
   * Phase 6, revisit if that turns out to matter in practice.
   */
  isUserConnected(userId: string): boolean;
}
