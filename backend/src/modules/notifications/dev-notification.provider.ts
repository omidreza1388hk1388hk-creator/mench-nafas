import { Injectable, Logger } from '@nestjs/common';
import { NotificationPayload, NotificationProvider } from './notification-provider.interface';

/**
 * Development-only push delivery. Logs what would have been sent instead
 * of calling Firebase — used whenever NOTIFICATIONS_PROVIDER=dev, or
 * automatically as a fallback if Firebase credentials are missing/invalid
 * at startup (see notifications.module.ts's factory). Never throws, never
 * requires any external credential, so the rest of the app (messaging,
 * groups, everything) keeps working fully with push simply not delivered
 * — this is the graceful-degradation path, not an error path.
 */
@Injectable()
export class DevNotificationProvider implements NotificationProvider {
  private readonly logger = new Logger('DevNotificationProvider');

  async sendToDevice(token: string, payload: NotificationPayload): Promise<void> {
    this.logger.warn(
      `[DEV ONLY] push -> token=${token.slice(0, 8)}... title="${payload.title}" ` +
        `body=${payload.body ? `"${payload.body}"` : '(none)'} collapseKey=${payload.collapseKey} ` +
        `— not sent via real FCM`,
    );
  }
}
