import { ForbiddenException, Injectable, Optional, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { OtpService } from './otp.service';
import { UsersService } from '../users/users.service';
import { DevicesRepository } from '../devices/devices.repository';
import { SessionsRepository } from '../sessions/sessions.repository';
import { TokenService } from '../sessions/token.service';
import { TwoStepChallenge, TwoStepService } from '../two-step/two-step.service';

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
    private readonly config: ConfigService,
    private readonly otpService: OtpService,
    private readonly usersService: UsersService,
    private readonly devicesRepository: DevicesRepository,
    private readonly sessionsRepository: SessionsRepository,
    private readonly tokenService: TokenService,
    @Optional() private readonly twoStep?: TwoStepService,
  ) {}

  requestOtp(phoneE164: string): Promise<{ challengeId: string }> {
    this.assertPhoneAllowed(phoneE164);
    return this.otpService.requestOtp(phoneE164);
  }

  async verifyOtpAndCreateSession(
    phoneE164: string,
    challengeId: string,
    code: string,
    deviceName: string,
  ): Promise<AuthTokens | TwoStepChallenge> {
    // Checked again here, not just in requestOtp: defense in depth against
    // any path that could reach verify without going through request
    // first (and cheap — this never touches the DB or Redis).
    this.assertPhoneAllowed(phoneE164);

    const isValid = await this.otpService.verifyOtp(phoneE164, challengeId, code);
    if (!isValid) {
      throw new UnauthorizedException('Invalid or expired code');
    }

    const user = await this.usersService.findOrCreateByPhone(phoneE164);

    // Two-step verification (Phase 4C): the SMS code was right, but this account also has a
    // password. No session yet — hand back a short-lived ticket; /auth/two-step/verify finishes it.
    if (this.twoStep && (await this.twoStep.isEnabled(user.id))) {
      return this.twoStep.startChallenge(user.id, deviceName);
    }

    const device = await this.devicesRepository.create(user.id, deviceName, 'android');
    return this.issueSession(user.id, device.id);
  }

  /** Second half of a two-step login. */
  async completeTwoStep(ticket: string, password: string): Promise<AuthTokens> {
    if (!this.twoStep) throw new UnauthorizedException('Two-step verification is not available');
    const { userId, deviceName } = await this.twoStep.verifyTicket(ticket, password);
    const device = await this.devicesRepository.create(userId, deviceName, 'android');
    return this.issueSession(userId, device.id);
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

  /**
   * When ALLOWED_PHONE_NUMBERS is set, rejects any phone not on the list
   * before an OTP is ever requested or verified — for a private
   * two-person messenger, this is what actually keeps a stranger who
   * finds the API from creating an account (OTP delivery alone doesn't:
   * anyone can request one for their own real number). Left unset, the
   * list is empty and every phone number is allowed, same as before this
   * existed.
   */
  private assertPhoneAllowed(phoneE164: string): void {
    const raw = this.config.get<string>('ALLOWED_PHONE_NUMBERS');
    if (!raw) return;

    const allowed = raw
      .split(',')
      .map((p) => p.trim())
      .filter(Boolean);
    if (allowed.length === 0) return;

    if (!allowed.includes(phoneE164)) {
      throw new ForbiddenException('Registration is not open to this phone number');
    }
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
