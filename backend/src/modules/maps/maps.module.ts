import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { FetchLike } from '../gifs/gifs.service';
import { SessionsModule } from '../sessions/sessions.module';
import { MapsController } from './maps.controller';
import { MapsService } from './maps.service';

@Module({
  imports: [SessionsModule],
  controllers: [MapsController],
  providers: [
    {
      provide: MapsService,
      inject: [ConfigService],
      useFactory: (config: ConfigService) => new MapsService(config, fetch as unknown as FetchLike),
    },
  ],
})
export class MapsModule {}
