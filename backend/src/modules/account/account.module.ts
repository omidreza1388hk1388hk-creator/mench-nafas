import { Module } from '@nestjs/common';
import { AccountController } from './account.controller';
import { AdminController } from './admin.controller';
import { AccountRepository } from './account.repository';
import { AccountService } from './account.service';
import { SessionsModule } from '../sessions/sessions.module';
import { SafetyModule } from '../safety/safety.module';

@Module({
  imports: [SessionsModule, SafetyModule],
  controllers: [AccountController, AdminController],
  providers: [AccountRepository, AccountService],
  exports: [AccountService],
})
export class AccountModule {}
