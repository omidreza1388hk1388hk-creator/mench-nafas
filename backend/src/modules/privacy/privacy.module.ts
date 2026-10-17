import { Global, Module } from '@nestjs/common';
import { PrivacyRepository } from './privacy.repository';
import { PrivacyService } from './privacy.service';
import { PrivacyController, DisappearingController } from './privacy.controller';
import { ExpirySweeper } from './expiry.sweeper';
import { RealtimeModule } from '../realtime/realtime.module';
import { SessionsModule } from '../sessions/sessions.module';

/** Global so Users / Calls / Messages can use PrivacyService (as an @Optional dependency) without new module imports. */
@Global()
@Module({
  imports: [RealtimeModule, SessionsModule],
  controllers: [PrivacyController, DisappearingController],
  providers: [PrivacyRepository, PrivacyService, ExpirySweeper],
  exports: [PrivacyService],
})
export class PrivacyModule {}
