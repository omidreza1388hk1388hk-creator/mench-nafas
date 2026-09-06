import { ConfigService } from '@nestjs/config';
import { BadRequestException } from '@nestjs/common';
import { OtpService } from './otp.service';
import { OtpRepository, OtpChallengeRow } from './otp.repository';
import { SmsProvider } from './sms-provider.interface';

describe('OtpService', () => {
  let service: OtpService;
  let otpRepository: jest.Mocked<OtpRepository>;
  let smsProvider: jest.Mocked<SmsProvider>;
  let redis: any;
  let config: ConfigService;

  const phone = '+15551234567';

  beforeEach(() => {
    otpRepository = {
      create: jest.fn(),
      findActiveById: jest.fn(),
      incrementAttempts: jest.fn(),
      consume: jest.fn(),
      mostRecentForPhone: jest.fn(),
    } as unknown as jest.Mocked<OtpRepository>;

    smsProvider = { sendOtp: jest.fn() };

    redis = { get: jest.fn().mockResolvedValue(null), set: jest.fn() };

    config = new ConfigService({
      OTP_LENGTH: 5,
      OTP_TTL_SECONDS: 120,
      OTP_MAX_ATTEMPTS: 5,
      OTP_RESEND_COOLDOWN_SECONDS: 45,
    });

    service = new OtpService(config, otpRepository, smsProvider, redis);
  });

  it('rejects a new OTP request while the resend cooldown is active', async () => {
    redis.get.mockResolvedValueOnce('1');
    await expect(service.requestOtp(phone)).rejects.toBeInstanceOf(BadRequestException);
    expect(otpRepository.create).not.toHaveBeenCalled();
  });

  it('creates a hashed (never plaintext) challenge and delivers via SmsProvider', async () => {
    otpRepository.create.mockResolvedValue({ id: 'challenge-1' } as OtpChallengeRow);

    const result = await service.requestOtp(phone);

    expect(result.challengeId).toBe('challenge-1');
    expect(smsProvider.sendOtp).toHaveBeenCalledTimes(1);
    const [, deliveredCode] = smsProvider.sendOtp.mock.calls[0];
    expect(deliveredCode).toMatch(/^\d{5}$/);

    const [, storedHash] = otpRepository.create.mock.calls[0];
    expect(storedHash).not.toEqual(deliveredCode); // never stored in plaintext
  });

  it('rejects verification once max attempts have been reached', async () => {
    otpRepository.findActiveById.mockResolvedValue({
      id: 'challenge-1',
      phone_e164: phone,
      code_hash: 'irrelevant',
      attempt_count: 5,
      max_attempts: 5,
      created_at: new Date(),
      expires_at: new Date(Date.now() + 60_000),
      consumed_at: null,
    });

    const ok = await service.verifyOtp(phone, 'challenge-1', '12345');
    expect(ok).toBe(false);
    expect(otpRepository.consume).not.toHaveBeenCalled();
  });

  it('rejects verification against a different phone number than the challenge was issued for', async () => {
    otpRepository.findActiveById.mockResolvedValue({
      id: 'challenge-1',
      phone_e164: phone,
      code_hash: 'irrelevant',
      attempt_count: 0,
      max_attempts: 5,
      created_at: new Date(),
      expires_at: new Date(Date.now() + 60_000),
      consumed_at: null,
    });

    const ok = await service.verifyOtp('+19998887777', 'challenge-1', '12345');
    expect(ok).toBe(false);
  });

  it('consumes the challenge exactly once on a correct code (single-use)', async () => {
    // Real request flow to get a real argon2 hash + the real plaintext code.
    otpRepository.create.mockImplementation(async (_phone, hash) => ({
      id: 'challenge-1',
      phone_e164: phone,
      code_hash: hash,
      attempt_count: 0,
      max_attempts: 5,
      created_at: new Date(),
      expires_at: new Date(Date.now() + 60_000),
      consumed_at: null,
    }));
    await service.requestOtp(phone);
    const [, plainCode] = smsProvider.sendOtp.mock.calls[0];
    const [, storedHash] = otpRepository.create.mock.calls[0];

    otpRepository.findActiveById.mockResolvedValue({
      id: 'challenge-1',
      phone_e164: phone,
      code_hash: storedHash,
      attempt_count: 0,
      max_attempts: 5,
      created_at: new Date(),
      expires_at: new Date(Date.now() + 60_000),
      consumed_at: null,
    });

    const ok = await service.verifyOtp(phone, 'challenge-1', plainCode);
    expect(ok).toBe(true);
    expect(otpRepository.consume).toHaveBeenCalledWith('challenge-1');
  });
});
