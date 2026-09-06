import { Injectable, UnauthorizedException } from '@nestjs/common';
import { OtpService } from './otp.service';
import { UsersService } from '../users/users.service';
import { DevicesRepository } from '../devices/devices.repository';
import { SessionsRepository } from '../sessions/sessions.repository';
import { TokenService } from '../sessions/token.service';

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  sessionId: string;
  userId: string;
  deviceId: string;
}

@Injectable()
export class AuthService {
  constructor(
    private readonly otpService: OtpService,
    private readonly usersService: UsersService,
    private readonly devicesRepository: DevicesRepository,
    private readonly sessionsRepository: SessionsRepository,
    private readonly tokenService: TokenService,
  ) {}

  requestOtp(phoneE164: string): Promise<{ challengeId: string }> {
    return this.otpService.requestOtp(phoneE164);
  }

  async verifyOtpAndCreateSession(
    phoneE164: string,
    challengeId: string,
    code: string,
    deviceName: string,
  ): Promise<AuthTokens> {
    const isValid = await this.otpService.verifyOtp(phoneE164, challengeId, code);
    if (!isValid) {
      throw new UnauthorizedException('Invalid or expired code');
    }

    const user = await this.usersService.findOrCreateByPhone(phoneE164);
    const device = await this.devicesRepository.create(user.id, deviceName, 'android');

    return this.issueSession(user.id, device.id);
  }

  async refreshSession(refreshToken: string): Promise<AuthTokens> {
    const hash = this.tokenService.hashRefreshToken(refreshToken);
    const session = await this.sessionsRepository.findActiveByTokenHash(hash);

    if (!session || !this.tokenService.refreshTokenMatchesHash(refreshToken, session.refresh_token_hash)) {
      throw new UnauthorizedException('Invalid or expired refresh token');
    }

    // Rotation: the old refresh token is revoked the moment it's used, and
    // a fresh one is issued and linked via replaced_by_id. If a revoked
    // token is ever presented again, that's a signal of possible
    // theft/replay — Phase 5 turns that signal into an active alert.
    const tokens = await this.issueSession(session.user_id, session.device_id);
    await this.sessionsRepository.rotate(session.id, tokens.sessionId);
    return tokens;
  }

  /**
   * Revokes the session tied to the presented refresh token. Ownership is
   * proven by possession of a valid, unexpired, unrevoked refresh token —
   * the same trust model /auth/token/refresh already uses (no separate
   * access-token guard). This matters in practice: a client whose access
   * token already expired must still be able to log out using only its
   * refresh token, and Android's logout call is intentionally made on the
   * unauthenticated HTTP client (see AuthRepositoryImpl.logout) since at
   * that point there may be nothing valid to attach as a Bearer header.
   */
  async logout(refreshToken: string): Promise<void> {
    const hash = this.tokenService.hashRefreshToken(refreshToken);
    const session = await this.sessionsRepository.findActiveByTokenHash(hash);

    if (!session) {
      // Nothing to revoke — logging out an already-dead session should
      // always succeed from the client's point of view, not error.
      return;
    }

    await this.sessionsRepository.revoke(session.id, session.user_id);
  }

  async logoutAllDevices(userId: string): Promise<void> {
    await this.sessionsRepository.revokeAllForUser(userId);
  }

  private async issueSession(userId: string, deviceId: string): Promise<AuthTokens> {
    const refreshToken = this.tokenService.generateRefreshToken();
    const refreshTokenHash = this.tokenService.hashRefreshToken(refreshToken);
    const expiresAt = this.tokenService.refreshTokenExpiry();

    const session = await this.sessionsRepository.create(userId, deviceId, refreshTokenHash, expiresAt);

    const accessToken = this.tokenService.signAccessToken({ sub: userId, deviceId });

    return { accessToken, refreshToken, sessionId: session.id, userId, deviceId };
  }
}
