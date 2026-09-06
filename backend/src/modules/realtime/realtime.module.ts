import { Module, forwardRef } from '@nestjs/common';
import { ChatGateway } from './chat.gateway';
import { REALTIME_BROADCASTER } from './realtime-broadcaster.interface';
import { SessionsModule } from '../sessions/sessions.module';
import { ConversationsModule } from '../conversations/conversations.module';
import { CallsRepositoryModule } from '../calls/calls-repository.module';

/**
 * forwardRef(() => ConversationsModule) here, mirrored by
 * forwardRef(() => RealtimeModule) in ConversationsModule's own imports
 * (Phase 6): this module needs ConversationsRepository (ChatGateway's
 * membership check on inbound typing events), and ConversationsModule
 * needs this module's REALTIME_BROADCASTER (ConversationsService emits
 * group.member_added / group.member_removed / group.member_left /
 * group.renamed / conversation.added on every group mutation). Without
 * forwardRef on both sides this is an unresolvable circular import — see
 * docs/ARCHITECTURE.md for why this was deferred through Phase 5 and
 * resolved this way rather than merging the two modules or reaching
 * across module boundaries directly.
 *
 * CallsRepositoryModule, not CallsModule — see the comment atop
 * CallsRepository for why importing the full CallsModule here would be a
 * (different) circular dependency.
 */
@Module({
  imports: [SessionsModule, forwardRef(() => ConversationsModule), CallsRepositoryModule],
  providers: [
    ChatGateway,
    {
      provide: REALTIME_BROADCASTER,
      useExisting: ChatGateway,
    },
  ],
  exports: [REALTIME_BROADCASTER],
})
export class RealtimeModule {}
