import { Module } from '@nestjs/common';
import { SessionsModule } from '../sessions/sessions.module';
import { SyncController } from './sync.controller';
import { SyncService } from './sync.service';

@Module({ imports: [SessionsModule], controllers: [SyncController], providers: [SyncService] })
export class SyncModule {}
