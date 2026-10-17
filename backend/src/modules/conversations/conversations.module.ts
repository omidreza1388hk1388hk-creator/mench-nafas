import { Module, forwardRef } from '@nestjs/common';
import { ConversationsRepository } from './conversations.repository';
import { ConversationsService } from './conversations.service';
import { ConversationsController } from './conversations.controller';
import { UsersModule } from '../users/users.module';
import { SessionsModule } from '../sessions/sessions.module';
import { RealtimeModule } from '../realtime/realtime.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { LettersController } from '../letters/letters.controller';
import { LettersRepository } from '../letters/letters.repository';
import { LettersService } from '../letters/letters.service';
import { LetterReminderJob } from '../letters/letter-reminder.job';
import { AlbumController } from '../album/album.controller';
import { AlbumRepository } from '../album/album.repository';
import { AlbumService } from '../album/album.service';
import { QuizzesController } from '../quizzes/quizzes.controller';
import { QuizzesRepository } from '../quizzes/quizzes.repository';
import { QuizzesService } from '../quizzes/quizzes.service';
import { VaultController } from '../vault/vault.controller';
import { VaultRepository } from '../vault/vault.repository';
import { VaultService } from '../vault/vault.service';
import { StoriesController, StoriesInboxController } from '../stories/stories.controller';
import { StoriesRepository } from '../stories/stories.repository';
import { StoriesService } from '../stories/stories.service';
import { CanvasController } from '../canvas/canvas.controller';
import { CanvasRepository } from '../canvas/canvas.repository';
import { CanvasService } from '../canvas/canvas.service';
import { CountersController } from '../counters/counters.controller';
import { CountersRepository } from '../counters/counters.repository';
import { CountersService } from '../counters/counters.service';
import { MemoriesController } from '../memories/memories.controller';
import { MemoriesRepository } from '../memories/memories.repository';
import { MemoriesService } from '../memories/memories.service';
import { CoupleController } from '../couple/couple.controller';
import { LinkPreviewController } from '../link-preview/link-preview.controller';
import { LinkPreviewService } from '../link-preview/link-preview.service';
import { CoupleRepository } from '../couple/couple.repository';
import { CoupleService } from '../couple/couple.service';

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
  controllers: [ConversationsController, LettersController, MemoriesController, CountersController, CanvasController, StoriesController, StoriesInboxController, VaultController, QuizzesController, AlbumController, CoupleController, LinkPreviewController],
  providers: [ConversationsRepository, ConversationsService, LettersRepository, LettersService, LetterReminderJob, MemoriesRepository, MemoriesService, CountersRepository, CountersService, CanvasRepository, CanvasService, StoriesRepository, StoriesService, VaultRepository, VaultService, QuizzesRepository, QuizzesService, AlbumRepository, AlbumService, CoupleRepository, CoupleService, LinkPreviewService],
  exports: [ConversationsRepository, ConversationsService],
})
export class ConversationsModule {}
