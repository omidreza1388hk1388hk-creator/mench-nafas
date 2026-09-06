import { Module } from '@nestjs/common';
import { SessionsRepository } from './sessions.repository';
import { TokenService } from './token.service';
import { JwtAuthGuard } from './jwt-auth.guard';

@Module({
  providers: [SessionsRepository, TokenService, JwtAuthGuard],
  exports: [SessionsRepository, TokenService, JwtAuthGuard],
})
export class SessionsModule {}
