import { Module, forwardRef } from '@nestjs/common';
import { ConversationsRepository } from './conversations.repository';
import { ConversationsService } from './conversations.service';
import { ConversationsController } from './conversations.controller';
import { UsersModule } from '../users/users.module';
import { SessionsModule } from '../sessions/sessions.module';
import { RealtimeModule } from '../realtime/realtime.module';
import { NotificationsModule } from '../notifications/notifications.module';

/**
 * forwardRef(() => RealtimeModule) here, mirrored by
 * forwardRef(() => ConversationsModule) in RealtimeModule's own imports
 * (Phase 6): RealtimeModule needs ConversationsRepository (ChatGateway's
 * membership check on inbound typing events), and this module needs
 * RealtimeModule's REALTIME_BROADCASTER (ConversationsService now emits
 * group.member_added / group.member_removed / group.member_left /
 * group.renamed / conversation.added on every group mutation). Without
 * forwardRef on both sides this is an unresolvable circular import —
 * see docs/ARCHITECTURE.md for why this was deferred through Phase 5 and
 * resolved this way rather than merging the two modules or reaching
 * across module boundaries directly.
 *
 * NotificationsModule is imported plainly (no forwardRef) even though it
 * itself imports RealtimeModule — it does NOT import ConversationsModule,
 * so this edge alone isn't part of any cycle; the only cycle in this
 * graph is the ConversationsModule<->RealtimeModule one above, and that
 * single forwardRef pair is what breaks it regardless of how many other
 * modules (like this one) also depend on RealtimeModule.
 */
@Module({
  imports: [UsersModule, SessionsModule, forwardRef(() => RealtimeModule), NotificationsModule],
  controllers: [ConversationsController],
  providers: [ConversationsRepository, ConversationsService],
  exports: [ConversationsRepository, ConversationsService],
})
export class ConversationsModule {}
