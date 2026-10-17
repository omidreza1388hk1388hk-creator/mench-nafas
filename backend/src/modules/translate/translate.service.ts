import { BadRequestException, HttpException, HttpStatus, Injectable, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

export const TRANSLATE_TARGETS = ['fa', 'en', 'ar'] as const;
export type TranslateTarget = (typeof TRANSLATE_TARGETS)[number];

/**
 * Message translation through a LibreTranslate-compatible server (Phase 2A). The provider is configured with
 * TRANSLATE_URL (e.g. https://libretranslate.example/translate) and an optional TRANSLATE_API_KEY. Without TRANSLATE_URL the
 * endpoint answers 503 and the app shows "translation is not available". Message text is sent only to the provider
 * the operator configured; nothing is stored.
 */
@Injectable()
export class TranslateService {
  constructor(private readonly config: ConfigService) {}

  async translate(text: string, target: TranslateTarget): Promise<{ translatedText: string; target: TranslateTarget }> {
    const url = this.config.get<string>('TRANSLATE_URL');
    if (!url) throw new ServiceUnavailableException('Translation is not configured');
    const trimmed = text.trim();
    if (!trimmed) throw new BadRequestException('Nothing to translate');
    if (!TRANSLATE_TARGETS.includes(target)) throw new BadRequestException('Unsupported language');

    let response: Response;
    try {
      response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          q: trimmed.slice(0, 2000),
          source: 'auto',
          target,
          format: 'text',
          ...(this.config.get<string>('TRANSLATE_API_KEY') ? { api_key: this.config.get<string>('TRANSLATE_API_KEY') } : {}),
        }),
        signal: AbortSignal.timeout(8000),
      });
    } catch {
      throw new HttpException('Translation provider unreachable', HttpStatus.BAD_GATEWAY);
    }
    if (!response.ok) throw new HttpException('Translation provider error', HttpStatus.BAD_GATEWAY);
    const body = (await response.json()) as { translatedText?: unknown };
    if (typeof body.translatedText !== 'string') throw new HttpException('Bad provider response', HttpStatus.BAD_GATEWAY);
    return { translatedText: body.translatedText, target };
  }
}
