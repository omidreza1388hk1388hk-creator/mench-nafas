import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { OtpService } from './otp.service';
import { OtpRepository } from './otp.repository';
import { SmsProvider } from './sms-provider.interface';
import { DevSmsProvider } from './dev-sms.provider';
import { SMS_PROVIDER } from './otp.service';
import { UsersModule } from '../users/users.module';
import { DevicesModule } from '../devices/devices.module';
import { SessionsModule } from '../sessions/sessions.module';

// JwtAuthGuard lives in SessionsModule, not here — see
// sessions/jwt-auth.guard.ts for why: its only dependency is
// TokenService, and AuthModule already depends on UsersModule, so
// AuthModule owning the guard as well would make UsersModule -> AuthModule
// (for the guard) and AuthModule -> UsersModule a circular import the
// moment any other module needed both a guard and user lookups (which
// UsersController does — see users.module.ts). Importing SessionsModule
// below is what makes JwtAuthGuard available to AuthController.

@Module({
  imports: [UsersModule, DevicesModule, SessionsModule],
  controllers: [AuthController],
  providers: [
    AuthService,
    OtpService,
    OtpRepository,
    {
      // Swap point for Phase 2+: provide a real SmsProvider implementation
      // here (behind the same interface) once OTP_PROVIDER=sms is wired to
      // an actual carrier. Nothing else in the module needs to change.
      provide: SMS_PROVIDER,
      inject: [ConfigService],
      useFactory: (config: ConfigService): SmsProvider => {
        const provider = config.get<string>('OTP_PROVIDER');
        if (provider === 'dev') {
          return new DevSmsProvider();
        }
        throw new Error(
          `OTP_PROVIDER="${provider}" has no implementation yet. ` +
            `A real SmsProvider must be added in Phase 2+ before this can be used.`,
        );
      },
    },
  ],
})
export class AuthModule {}
