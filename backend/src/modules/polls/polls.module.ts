import { Module } from '@nestjs/common';
import { ConversationsModule } from '../conversations/conversations.module';
import { RealtimeModule } from '../realtime/realtime.module';
import { SessionsModule } from '../sessions/sessions.module';
import { PollsController } from './polls.controller';
import { PollsRepository } from './polls.repository';
import { PollsService } from './polls.service';

@Module({
  imports: [ConversationsModule, RealtimeModule, SessionsModule],
  controllers: [PollsController],
  providers: [PollsRepository, PollsService],
})
export class PollsModule {}
