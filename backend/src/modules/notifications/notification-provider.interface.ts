export const NOTIFICATION_PROVIDER = 'NOTIFICATION_PROVIDER';

export interface NotificationPayload {
  title: string;
  /** Null for the rare case there's genuinely nothing to show as a body — providers must handle this without crashing, not assume it's always present. */
  body: string | null;
  /**
   * Small string-keyed data payload for the Android client to act on when
   * the notification is tapped — at minimum conversationId, so
   * MenchFirebaseMessagingService can deep-link into the right chat via
   * the existing MenchNavGraph route instead of just opening the app to
   * its default screen.
   */
  data: Record<string, string>;
  /**
   * Notifications for the same conversation collapse into one OS
   * notification instead of stacking into a wall of separate ones when
   * several messages arrive in quick succession. Typically the
   * conversationId.
   */
  collapseKey: string;
}

/**
 * Boundary between MENCH and whatever real push-delivery provider is
 * configured — same pattern as SmsProvider (backend/src/modules/auth/
 * sms-provider.interface.ts) and StorageProvider
 * (backend/src/storage/storage-provider.interface.ts). Phase 6 ships two
 * implementations:
 *   - DevNotificationProvider: logs instead of sending, used whenever
 *     NOTIFICATIONS_PROVIDER=dev or Firebase credentials are absent.
 *   - FcmNotificationProvider: real Firebase Cloud Messaging delivery.
 *
 * No code outside this file (and NotificationsService, which decides
 * WHEN to notify) should ever need to know which provider is active.
 */
export interface NotificationProvider {
  /**
   * Must never throw for a single bad/expired token — one device's dead
   * token must not block notifying a user's other devices, or block the
   * message-send request path this is called from. Implementations catch
   * and log their own delivery errors internally; callers treat this as
   * fire-and-forget.
   */
  sendToDevice(token: string, payload: NotificationPayload): Promise<void>;
}
