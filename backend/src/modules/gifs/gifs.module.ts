import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SessionsModule } from '../sessions/sessions.module';
import { GifsController } from './gifs.controller';
import { FetchLike, GifsService } from './gifs.service';

@Module({
  imports: [SessionsModule],
  controllers: [GifsController],
  providers: [
    {
      provide: GifsService,
      inject: [ConfigService],
      useFactory: (config: ConfigService) => new GifsService(config, fetch as unknown as FetchLike),
    },
  ],
})
export class GifsModule {}
