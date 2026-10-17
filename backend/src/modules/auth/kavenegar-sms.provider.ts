import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SmsProvider } from './sms-provider.interface';

/**
 * Real OTP delivery via Kavenegar's Verify/Lookup API — the endpoint
 * Kavenegar specifically designs for one-time codes (higher deliverability
 * than a generic SMS send, and works even for numbers that have blocked
 * promotional SMS). Docs: https://kavenegar.com/rest.html#verify-lookup
 *
 * Requires a template to be created once in the Kavenegar panel
 * (Settings -> Verification Templates) containing a single %token%
 * placeholder, e.g.: "کد تایید شما: %token%"
 *
 * Config:
 *   KAVENEGAR_API_KEY   - from the Kavenegar panel (API keys page)
 *   KAVENEGAR_TEMPLATE  - the template name created in the panel
 */
@Injectable()
export class KavenegarSmsProvider implements SmsProvider {
  private readonly logger = new Logger('KavenegarSmsProvider');

  constructor(private readonly config: ConfigService) {}

  async sendOtp(phoneE164: string, code: string): Promise<void> {
    const apiKey = this.config.get<string>('KAVENEGAR_API_KEY');
    const template = this.config.get<string>('KAVENEGAR_TEMPLATE');

    if (!apiKey || !template) {
      throw new Error(
        'KAVENEGAR_API_KEY and KAVENEGAR_TEMPLATE must be set when OTP_PROVIDER=kavenegar',
      );
    }

    // Kavenegar's lookup API expects a local-format Iranian number
    // (0912...) rather than E.164 (+98912...) — strip the +98 prefix.
    const receptor = phoneE164.startsWith('+98')
      ? '0' + phoneE164.slice(3)
      : phoneE164;

    const url = new URL(
      `https://api.kavenegar.com/v1/${apiKey}/verify/lookup.json`,
    );
    url.searchParams.set('receptor', receptor);
    url.searchParams.set('token', code);
    url.searchParams.set('template', template);

    const response = await fetch(url.toString());
    const body = (await response.json()) as {
      return?: { status?: number; message?: string };
    };

    if (!response.ok || body?.return?.status !== 200) {
      this.logger.error(
        `Kavenegar OTP send failed for ${receptor}: ${JSON.stringify(body)}`,
      );
      throw new Error(
        `Kavenegar OTP send failed: ${body?.return?.message ?? response.statusText}`,
      );
    }
  }
}
