import { ForbiddenException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AuthService } from './auth.service';

function build(allowed?: string) {
  const otp = {
    requestOtp: jest.fn().mockResolvedValue({ challengeId: 'c1' }),
    verifyOtp: jest.fn(),
  };
  const service = new AuthService(
    new ConfigService(allowed === undefined ? {} : { ALLOWED_PHONE_NUMBERS: allowed }),
    otp as never,
    {} as never,
    {} as never,
    {} as never,
    {} as never,
  );
  return { service, otp };
}

describe('AuthService phone allowlist', () => {
  it('allows any number when ALLOWED_PHONE_NUMBERS is unset', async () => {
    const { service, otp } = build();
    await expect(service.requestOtp('+989121111111')).resolves.toEqual({ challengeId: 'c1' });
    expect(otp.requestOtp).toHaveBeenCalled();
  });

  it('allows a listed number (whitespace tolerated)', async () => {
    const { service } = build('+989121111111, +989122222222');
    await expect(service.requestOtp('+989122222222')).resolves.toBeDefined();
  });

  it('rejects an unlisted number before any OTP is sent', async () => {
    const { service, otp } = build('+989121111111,+989122222222');
    expect(() => service.requestOtp('+989123333333')).toThrow(ForbiddenException);
    expect(otp.requestOtp).not.toHaveBeenCalled();
  });

  it('also rejects an unlisted number at verify time', async () => {
    const { service, otp } = build('+989121111111');
    await expect(
      service.verifyOtpAndCreateSession('+989123333333', 'c', '12345', 'phone'),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(otp.verifyOtp).not.toHaveBeenCalled();
  });
});
