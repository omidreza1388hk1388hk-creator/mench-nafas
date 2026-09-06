import { Module } from '@nestjs/common';
import { CallsController } from './calls.controller';
import { CallsService } from './calls.service';
import { IceServersService } from './ice-servers.service';
import { CallsRepositoryModule } from './calls-repository.module';
import { ConversationsModule } from '../conversations/conversations.module';
import { UsersModule } from '../users/users.module';
import { RealtimeModule } from '../realtime/realtime.module';
import { SessionsModule } from '../sessions/sessions.module';

@Module({
  imports: [CallsRepositoryModule, ConversationsModule, UsersModule, RealtimeModule, SessionsModule],
  controllers: [CallsController],
  providers: [CallsService, IceServersService],
})
export class CallsModule {}
