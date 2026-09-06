import { Body, Controller, HttpCode, HttpStatus, Post, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { RequestOtpDto } from './dto/request-otp.dto';
import { VerifyOtpDto } from './dto/verify-otp.dto';
import { RefreshTokenDto } from './dto/refresh-token.dto';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  // Stricter limit than the global default — OTP requests are the most
  // abuse-prone endpoint in the whole API (SMS pumping / cost attacks).
  @Throttle({ default: { limit: 3, ttl: 60_000 } })
  @Post('otp/request')
  @HttpCode(HttpStatus.OK)
  async requestOtp(@Body() dto: RequestOtpDto) {
    const result = await this.authService.requestOtp(dto.phone);
    return { challengeId: result.challengeId };
  }

  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('otp/verify')
  @HttpCode(HttpStatus.OK)
  async verifyOtp(@Body() dto: VerifyOtpDto) {
    const tokens = await this.authService.verifyOtpAndCreateSession(
      dto.phone,
      dto.challengeId,
      dto.code,
      dto.deviceName,
    );
    return {
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      userId: tokens.userId,
      deviceId: tokens.deviceId,
    };
  }

  @Post('token/refresh')
  @HttpCode(HttpStatus.OK)
  async refresh(@Body() dto: RefreshTokenDto) {
    const tokens = await this.authService.refreshSession(dto.refreshToken);
    return {
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
    };
  }

  @Post('logout')
  @HttpCode(HttpStatus.NO_CONTENT)
  async logout(@Body() dto: RefreshTokenDto) {
    await this.authService.logout(dto.refreshToken);
  }

  @UseGuards(JwtAuthGuard)
  @Post('logout-all')
  @HttpCode(HttpStatus.NO_CONTENT)
  async logoutAll(@Req() req: any) {
    await this.authService.logoutAllDevices(req.user.sub);
  }
}
