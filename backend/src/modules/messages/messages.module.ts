import { Module } from '@nestjs/common';
import { MessagesRepository } from './messages.repository';
import { MessagesService } from './messages.service';
import { MessagesController } from './messages.controller';
import { ConversationsModule } from '../conversations/conversations.module';
import { RealtimeModule } from '../realtime/realtime.module';
import { SessionsModule } from '../sessions/sessions.module';
import { AttachmentsModule } from '../attachments/attachments.module';
import { UsersModule } from '../users/users.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { AccountModule } from '../account/account.module';

@Module({
  imports: [
    ConversationsModule,
    RealtimeModule,
    SessionsModule,
    AttachmentsModule,
    UsersModule,
    NotificationsModule,
    AccountModule,
  ],
  controllers: [MessagesController],
  providers: [MessagesRepository, MessagesService],
  exports: [MessagesService],
})
export class MessagesModule {}
