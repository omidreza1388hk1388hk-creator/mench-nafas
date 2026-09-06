import { Injectable, Logger } from '@nestjs/common';
import { SmsProvider } from './sms-provider.interface';

/**
 * Development-only OTP delivery. Prints the code to the server log instead
 * of sending a real SMS. env.validation.ts refuses to boot with
 * OTP_PROVIDER=dev when NODE_ENV=production, so this class can never be
 * wired into a production deployment by configuration alone.
 */
@Injectable()
export class DevSmsProvider implements SmsProvider {
  private readonly logger = new Logger('DevSmsProvider');

  async sendOtp(phoneE164: string, code: string): Promise<void> {
    this.logger.warn(
      `[DEV ONLY] OTP for ${phoneE164}: ${code} — not sent via real SMS`,
    );
  }
}
