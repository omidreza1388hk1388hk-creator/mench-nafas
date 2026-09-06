import { BadRequestException, Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as argon2 from 'argon2';
import * as crypto from 'crypto';
import Redis from 'ioredis';
import { REDIS_CLIENT } from '../../redis/redis.module';
import { OtpRepository, OtpChallengeRow } from './otp.repository';
import { SmsProvider } from './sms-provider.interface';

export const SMS_PROVIDER = 'SMS_PROVIDER';

/**
 * Generates, stores, rate-limits, and verifies OTP challenges.
 * Codes are never stored or logged in plaintext — only their argon2 hash.
 */
@Injectable()
export class OtpService {
  constructor(
    private readonly config: ConfigService,
    private readonly otpRepository: OtpRepository,
    @Inject(SMS_PROVIDER) private readonly smsProvider: SmsProvider,
    @Inject(REDIS_CLIENT) private readonly redis: Redis,
  ) {}

  async requestOtp(phoneE164: string): Promise<{ challengeId: string }> {
    const cooldownSeconds = this.config.get<number>('OTP_RESEND_COOLDOWN_SECONDS') as number;
    const cooldownKey = `otp:cooldown:${phoneE164}`;

    const stillCoolingDown = await this.redis.get(cooldownKey);
    if (stillCoolingDown) {
      throw new BadRequestException('Please wait before requesting another code');
    }

    const length = this.config.get<number>('OTP_LENGTH') as number;
    const ttlSeconds = this.config.get<number>('OTP_TTL_SECONDS') as number;
    const maxAttempts = this.config.get<number>('OTP_MAX_ATTEMPTS') as number;

    const code = this.generateNumericCode(length);
    const codeHash = await argon2.hash(code);
    const expiresAt = new Date(Date.now() + ttlSeconds * 1000);

    const challenge = await this.otpRepository.create(phoneE164, codeHash, maxAttempts, expiresAt);

    await this.redis.set(cooldownKey, '1', 'EX', cooldownSeconds);
    await this.smsProvider.sendOtp(phoneE164, code);

    return { challengeId: challenge.id };
  }

  async verifyOtp(phoneE164: string, challengeId: string, code: string): Promise<boolean> {
    const challenge: OtpChallengeRow | null = await this.otpRepository.findActiveById(challengeId);

    if (!challenge || challenge.phone_e164 !== phoneE164) {
      return false;
    }

    if (challenge.attempt_count >= challenge.max_attempts) {
      return false;
    }

    await this.otpRepository.incrementAttempts(challenge.id);

    const isValid = await argon2.verify(challenge.code_hash, code);
    if (!isValid) {
      return false;
    }

    await this.otpRepository.consume(challenge.id);
    return true;
  }

  private generateNumericCode(length: number): string {
    // crypto.randomInt is CSPRNG-backed and unbiased for the given range,
    // unlike Math.random(), which must never be used for anything
    // security-sensitive.
    const min = 10 ** (length - 1);
    const max = 10 ** length - 1;
    return crypto.randomInt(min, max + 1).toString();
  }
}
