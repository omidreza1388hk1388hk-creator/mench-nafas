import { BadRequestException, HttpException, ServiceUnavailableException } from '@nestjs/common';
import { TranslateService } from './translate.service';

describe('TranslateService', () => {
  const config = (values: Record<string, string>) => ({ get: (k: string) => values[k] }) as never;
  const realFetch = global.fetch;
  afterEach(() => { global.fetch = realFetch; });

  it('answers 503 when no provider is configured', async () => {
    await expect(new TranslateService(config({})).translate('hi', 'fa')).rejects.toBeInstanceOf(ServiceUnavailableException);
  });

  it('rejects empty text', async () => {
    await expect(new TranslateService(config({ TRANSLATE_URL: 'http://x' })).translate('   ', 'fa')).rejects.toBeInstanceOf(BadRequestException);
  });

  it('returns the provider translation and sends target + api key', async () => {
    const fetchMock = jest.fn().mockResolvedValue({ ok: true, json: async () => ({ translatedText: 'سلام' }) });
    global.fetch = fetchMock as never;
    const service = new TranslateService(config({ TRANSLATE_URL: 'http://x/translate', TRANSLATE_API_KEY: 'k' }));
    await expect(service.translate('hello', 'fa')).resolves.toEqual({ translatedText: 'سلام', target: 'fa' });
    const sent = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(sent).toMatchObject({ q: 'hello', target: 'fa', api_key: 'k' });
  });

  it('maps provider failures to 502', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false }) as never;
    await expect(new TranslateService(config({ TRANSLATE_URL: 'http://x' })).translate('hello', 'en')).rejects.toBeInstanceOf(HttpException);
  });
});
