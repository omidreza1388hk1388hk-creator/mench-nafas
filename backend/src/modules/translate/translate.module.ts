import { Module } from '@nestjs/common';
import { SessionsModule } from '../sessions/sessions.module';
import { TranslateController } from './translate.controller';
import { TranslateService } from './translate.service';

@Module({ imports: [SessionsModule], controllers: [TranslateController], providers: [TranslateService] })
export class TranslateModule {}
