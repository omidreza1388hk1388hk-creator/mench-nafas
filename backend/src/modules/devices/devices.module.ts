import { Module } from '@nestjs/common';
import { DevicesRepository } from './devices.repository';
import { DevicesController } from './devices.controller';
import { SessionsModule } from '../sessions/sessions.module';

@Module({
  imports: [SessionsModule], // JwtAuthGuard, for DevicesController
  controllers: [DevicesController],
  providers: [DevicesRepository],
  exports: [DevicesRepository],
})
export class DevicesModule {}
