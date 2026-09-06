import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NotificationsService } from './notifications.service';
import { NOTIFICATION_PROVIDER, NotificationProvider } from './notification-provider.interface';
import { DevNotificationProvider } from './dev-notification.provider';
import { FcmNotificationProvider } from './fcm-notification.provider';
import { DevicesModule } from '../devices/devices.module';
import { UsersModule } from '../users/users.module';
import { RealtimeModule } from '../realtime/realtime.module';

const logger = new Logger('NotificationsModule');

@Module({
  imports: [DevicesModule, UsersModule, RealtimeModule],
  providers: [
    NotificationsService,
    {
      provide: NOTIFICATION_PROVIDER,
      inject: [ConfigService],
      useFactory: (config: ConfigService): NotificationProvider => {
        const provider = config.get<string>('NOTIFICATIONS_PROVIDER');
        if (provider !== 'fcm') {
          return new DevNotificationProvider();
        }

        const projectId = config.get<string>('FIREBASE_PROJECT_ID');
        const clientEmail = config.get<string>('FIREBASE_CLIENT_EMAIL');
        const privateKey = config.get<string>('FIREBASE_PRIVATE_KEY');

        // Graceful degradation (master-prompt principle #49): a
        // misconfigured or absent Firebase credential must never crash
        // startup or block messaging. Fall back to the dev provider and
        // say loudly why, instead of throwing like AuthModule's
        // SMS_PROVIDER factory does for an unimplemented OTP provider —
        // OTP delivery being broken is a hard failure worth blocking
        // startup for; push notifications being unavailable is not.
        if (!projectId || !clientEmail || !privateKey) {
          logger.warn(
            'NOTIFICATIONS_PROVIDER=fcm but FIREBASE_PROJECT_ID / FIREBASE_CLIENT_EMAIL / ' +
              'FIREBASE_PRIVATE_KEY are not fully set — falling back to DevNotificationProvider. ' +
              'Push notifications will be logged, not delivered, until real credentials are configured.',
          );
          return new DevNotificationProvider();
        }

        try {
          return new FcmNotificationProvider({ projectId, clientEmail, privateKey });
        } catch (err) {
          logger.error(
            `Failed to initialize Firebase — falling back to DevNotificationProvider: ${(err as Error).message}`,
          );
          return new DevNotificationProvider();
        }
      },
    },
  ],
  exports: [NotificationsService],
})
export class NotificationsModule {}
