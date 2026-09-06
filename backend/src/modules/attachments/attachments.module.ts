import { Module } from '@nestjs/common';
import { AttachmentsRepository } from './attachments.repository';
import { AttachmentsService } from './attachments.service';
import { AttachmentsController, AttachmentContentController } from './attachments.controller';
import { ConversationsModule } from '../conversations/conversations.module';
import { SessionsModule } from '../sessions/sessions.module';

// StorageModule is @Global (see storage/storage.module.ts) and only needs
// to be imported once, in AppModule — its STORAGE_PROVIDER export is
// already visible here without listing it, same as DatabaseModule/
// RedisModule are for every other module's repositories.
@Module({
  imports: [ConversationsModule, SessionsModule],
  controllers: [AttachmentsController, AttachmentContentController],
  providers: [AttachmentsRepository, AttachmentsService],
  exports: [AttachmentsRepository, AttachmentsService],
})
export class AttachmentsModule {}
