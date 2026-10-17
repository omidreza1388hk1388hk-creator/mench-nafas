import { Module } from '@nestjs/common';
import { ConversationsModule } from '../conversations/conversations.module';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { ConversationsService } from '../conversations/conversations.service';
import {
  REALTIME_BROADCASTER,
  RealtimeBroadcaster,
} from '../realtime/realtime-broadcaster.interface';
import { RealtimeModule } from '../realtime/realtime.module';
import { SessionsModule } from '../sessions/sessions.module';
import { GroupAdminController } from './group-admin.controller';
import { GroupAdminRepository } from './group-admin.repository';
import { GroupAdminService } from './group-admin.service';

@Module({
  imports: [ConversationsModule, RealtimeModule, SessionsModule],
  controllers: [GroupAdminController],
  providers: [
    GroupAdminRepository,
    {
      provide: GroupAdminService,
      inject: [
        GroupAdminRepository,
        ConversationsRepository,
        ConversationsService,
        REALTIME_BROADCASTER,
      ],
      useFactory: (
        repo: GroupAdminRepository,
        convRepo: ConversationsRepository,
        convService: ConversationsService,
        broadcaster: RealtimeBroadcaster,
      ) => new GroupAdminService(repo, convRepo, convService, broadcaster),
    },
  ],
})
export class GroupAdminModule {}
