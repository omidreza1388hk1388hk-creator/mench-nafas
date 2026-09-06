import { Module } from '@nestjs/common';
import { SearchRepository } from './search.repository';
import { SearchService } from './search.service';
import { SearchController } from './search.controller';
import { SessionsModule } from '../sessions/sessions.module';

@Module({
  imports: [SessionsModule],
  controllers: [SearchController],
  providers: [SearchRepository, SearchService],
})
export class SearchModule {}
