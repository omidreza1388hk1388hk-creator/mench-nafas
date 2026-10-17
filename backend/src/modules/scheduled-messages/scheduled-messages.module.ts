import { Module } from '@nestjs/common';
import { ConversationsModule } from '../conversations/conversations.module';
import { ConversationsService } from '../conversations/conversations.service';
import { MessagesModule } from '../messages/messages.module';
import { MessagesService } from '../messages/messages.service';
import { SessionsModule } from '../sessions/sessions.module';
import { ScheduledMessagesController } from './scheduled-messages.controller';
import { ScheduledMessagesRepository } from './scheduled-messages.repository';
import { ScheduledMessagesService } from './scheduled-messages.service';

@Module({
  imports: [ConversationsModule, MessagesModule, SessionsModule],
  controllers: [ScheduledMessagesController],
  providers: [
    ScheduledMessagesRepository,
    {
      provide: ScheduledMessagesService,
      inject: [ScheduledMessagesRepository, ConversationsService, MessagesService],
      useFactory: (
        repo: ScheduledMessagesRepository,
        conversations: ConversationsService,
        messages: MessagesService,
      ) => new ScheduledMessagesService(repo, conversations, messages),
    },
  ],
})
export class ScheduledMessagesModule {}
