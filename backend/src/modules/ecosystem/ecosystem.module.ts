import { Module } from '@nestjs/common';
import { SessionsModule } from '../sessions/sessions.module';
import { AccountModule } from '../account/account.module';
import { SafetyModule } from '../safety/safety.module';
import { GrowthService } from '../growth/growth.service';
import { CloudService } from '../cloud/cloud.service';
import { PlannerService } from '../planner/planner.service';
import { CloudController, GrowthController, PlannerController, SafetyController } from './ecosystem.controllers';
import { EcosystemAdminController } from './ecosystem-admin.controller';

@Module({
  imports: [SessionsModule, AccountModule, SafetyModule],
  controllers: [SafetyController, GrowthController, CloudController, PlannerController, EcosystemAdminController],
  providers: [GrowthService, CloudService, PlannerService],
})
export class EcosystemModule {}
