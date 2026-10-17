import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { ThrottlerModule } from '@nestjs/throttler';
import { envValidationSchema } from './config/env.validation';
import { DatabaseModule } from './database/database.module';
import { RedisModule } from './redis/redis.module';
import { StorageModule } from './storage/storage.module';
import { AuthModule } from './modules/auth/auth.module';
import { UsersModule } from './modules/users/users.module';
import { DevicesModule } from './modules/devices/devices.module';
import { SessionsModule } from './modules/sessions/sessions.module';
import { ConversationsModule } from './modules/conversations/conversations.module';
import { MessagesModule } from './modules/messages/messages.module';
import { RealtimeModule } from './modules/realtime/realtime.module';
import { AttachmentsModule } from './modules/attachments/attachments.module';
import { CallsModule } from './modules/calls/calls.module';
import { NotificationsModule } from './modules/notifications/notifications.module';
import { SearchModule } from './modules/search/search.module';
import { DiagnosticsModule } from './modules/diagnostics/diagnostics.module';
import { AccountModule } from './modules/account/account.module';
import { EcosystemModule } from './modules/ecosystem/ecosystem.module';
import { WebLoginModule } from './modules/weblogin/weblogin.module';
import { SyncModule } from './modules/sync/sync.module';
import { GifsModule } from './modules/gifs/gifs.module';
import { PollsModule } from './modules/polls/polls.module';
import { MapsModule } from './modules/maps/maps.module';
import { ScheduledMessagesModule } from './modules/scheduled-messages/scheduled-messages.module';
import { GroupAdminModule } from './modules/group-admin/group-admin.module';
import { TranslateModule } from './modules/translate/translate.module';
import { PrivacyModule } from './modules/privacy/privacy.module';
import { TwoStepModule } from './modules/two-step/two-step.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      validationSchema: envValidationSchema,
    }),
    // Global request rate limiting. Auth endpoints layer stricter,
    // OTP-specific limits on top of this via AuthThrottlerGuard.
    ThrottlerModule.forRoot([{ name: 'default', ttl: 60_000, limit: 120 }]),
    DatabaseModule,
    RedisModule,
    StorageModule,
    AuthModule,
    UsersModule,
    DevicesModule,
    SessionsModule,
    ConversationsModule,
    MessagesModule,
    RealtimeModule,
    AttachmentsModule,
    CallsModule,
    NotificationsModule,
    SearchModule,
    DiagnosticsModule,
    AccountModule,
    EcosystemModule,
    WebLoginModule,
    SyncModule,
    GifsModule,
    PollsModule,
    MapsModule,
    ScheduledMessagesModule,
    GroupAdminModule,
    TranslateModule,
    PrivacyModule,
    TwoStepModule,
  ],
})
export class AppModule {}
