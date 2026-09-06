import { Injectable, Logger } from '@nestjs/common';
import { App, cert, getApps, initializeApp } from 'firebase-admin/app';
import { getMessaging, Messaging } from 'firebase-admin/messaging';
import { NotificationPayload, NotificationProvider } from './notification-provider.interface';

export interface FcmCredentials {
  projectId: string;
  clientEmail: string;
  privateKey: string;
}

/**
 * Real push delivery via Firebase Cloud Messaging. Constructed only when
 * NOTIFICATIONS_PROVIDER=fcm AND all three credential fields are present
 * — see notifications.module.ts's factory for the fallback-to-dev
 * behavior when they're not.
 */
@Injectable()
export class FcmNotificationProvider implements NotificationProvider {
  private readonly logger = new Logger('FcmNotificationProvider');
  private readonly messaging: Messaging;

  constructor(credentials: FcmCredentials) {
    // Reuse an existing default app if one was already initialized
    // (relevant under Jest's module-reload-between-test-files behavior,
    // and if this provider is ever constructed more than once in the
    // same process) rather than calling initializeApp() unconditionally,
    // which throws on a second call.
    const app: App = getApps()[0] ?? initializeApp({
      credential: cert({
        projectId: credentials.projectId,
        clientEmail: credentials.clientEmail,
        // .env values commonly escape real newlines as literal "\n" —
        // Firebase's private key parser requires actual newline
        // characters, so this must be un-escaped here, once, at the
        // boundary — not left for every call site to remember.
        privateKey: credentials.privateKey.replace(/\\n/g, '\n'),
      }),
    });
    this.messaging = getMessaging(app);
  }

  async sendToDevice(token: string, payload: NotificationPayload): Promise<void> {
    try {
      await this.messaging.send({
        token,
        // Deliberately a DATA-ONLY message (no top-level `notification`
        // field) rather than the more common notification+data combo.
        // FCM's own behavior for a notification+data message differs by
        // app state — the OS displays it automatically (using ONLY the
        // notification fields) while the app is backgrounded, bypassing
        // MenchFirebaseMessagingService.onMessageReceived entirely, which
        // would silently break the tap-to-deep-link behavior for exactly
        // the backgrounded case that matters most. Data-only messages
        // always reach onMessageReceived (app-process-permitting — see
        // that method's own doc comment for the real, expected limit:
        // Android can still decline to wake a force-stopped app), so the
        // client controls notification building consistently in every
        // app state instead of two different code paths.
        data: { ...payload.data, title: payload.title, body: payload.body ?? '' },
        android: {
          collapseKey: payload.collapseKey,
          priority: 'high',
        },
      });
    } catch (err) {
      // A single dead/unregistered token (device uninstalled the app,
      // token rotated on Google's side, etc.) is routine and expected —
      // log it and move on. It must never bubble up and fail the
      // message-send request that triggered this notification, and it
      // must never block notifying the user's OTHER devices.
      this.logger.warn(`Failed to deliver push to a device: ${(err as Error).message}`);
    }
  }
}
