import { Module } from '@nestjs/common';
import { TwoStepRepository } from './two-step.repository';
import { TwoStepService } from './two-step.service';
import { TwoStepController } from './two-step.controller';
import { SessionsModule } from '../sessions/sessions.module';

@Module({
  imports: [SessionsModule],
  controllers: [TwoStepController],
  providers: [TwoStepRepository, TwoStepService],
  exports: [TwoStepService],
})
export class TwoStepModule {}
