import {
  BadGatewayException,
  BadRequestException,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { FetchLike, GifsService } from './gifs.service';

type FakeResponse = Awaited<ReturnType<FetchLike>>;

function jsonResponse(body: unknown, status = 200): FakeResponse {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
    arrayBuffer: async () => new ArrayBuffer(0),
  };
}

function binaryResponse(
  bytes: number,
  contentType = 'image/gif',
  declaredLength?: string,
): FakeResponse {
  return {
    ok: true,
    status: 200,
    headers: {
      get: (name: string) =>
        name.toLowerCase() === 'content-type'
          ? contentType
          : name.toLowerCase() === 'content-length'
            ? (declaredLength ?? String(bytes))
            : null,
    },
    json: async () => ({}),
    arrayBuffer: async () => new ArrayBuffer(bytes),
  };
}

const gif = (id: string, host = 'media1.giphy.com') => ({
  id,
  title: `title ${id}`,
  images: {
    fixed_width_small: { url: `https://${host}/media/${id}/100w.gif`, width: '100', height: '80' },
    fixed_width: { url: `https://${host}/media/${id}/200w.gif`, width: '200', height: '160' },
    downsized: {
      url: `https://${host}/media/${id}/giphy-downsized.gif`,
      width: '480',
      height: '384',
    },
  },
});

function build(
  handler: (url: string) => FakeResponse,
  env: Record<string, string | undefined> = { GIPHY_API_KEY: 'secret-key' },
) {
  const calls: string[] = [];
  const fetcher: FetchLike = async (url) => {
    calls.push(url);
    return handler(url);
  };
  const config = { get: (key: string) => env[key] } as unknown as ConfigService;
  return { service: new GifsService(config, fetcher), calls };
}

describe('GifsService', () => {
  it('maps search results, remembers URLs and reports the next offset', async () => {
    const { service, calls } = build(() =>
      jsonResponse({
        data: [gif('abcde12345'), gif('zzzzz99999')],
        pagination: { total_count: 100, count: 2 },
      }),
    );
    const page = await service.search('  romantic couple ', 0);
    expect(page.items).toEqual([
      { id: 'abcde12345', title: 'title abcde12345', width: 200, height: 160 },
      { id: 'zzzzz99999', title: 'title zzzzz99999', width: 200, height: 160 },
    ]);
    expect(page.nextOffset).toBe(2);
    expect(calls[0]).toContain('/search?');
    expect(calls[0]).toContain('q=romantic+couple');
    expect(calls[0]).toContain('rating=pg-13');
    expect(calls[0]).toContain('limit=24');
  });

  it('ends pagination when the provider has no more results', async () => {
    const { service } = build(() =>
      jsonResponse({ data: [gif('abcde12345')], pagination: { total_count: 1, count: 1 } }),
    );
    expect((await service.search('love', 0)).nextOffset).toBeNull();
  });

  it('skips results with an unsafe id or no usable URL', async () => {
    const { service } = build(() =>
      jsonResponse({
        data: [gif('bad/../id'), { id: 'nourl12345', images: {} }, gif('good123456')],
        pagination: { total_count: 3 },
      }),
    );
    const page = await service.search('love', 0);
    expect(page.items.map((i) => i.id)).toEqual(['good123456']);
  });

  it('validates the query and offset', async () => {
    const { service } = build(() => jsonResponse({ data: [] }));
    await expect(service.search('   ', 0)).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.search('x'.repeat(61), 0)).rejects.toBeInstanceOf(BadRequestException);
    const { service: s2, calls } = build(() => jsonResponse({ data: [] }));
    await s2.trending(99999);
    expect(calls[0]).toContain('/trending?');
    expect(calls[0]).toContain('offset=500');
  });

  it('answers 503 when no API key is configured, without calling out', async () => {
    const { service, calls } = build(() => jsonResponse({ data: [] }), {});
    await expect(service.search('love', 0)).rejects.toBeInstanceOf(ServiceUnavailableException);
    expect(calls).toHaveLength(0);
  });

  it('uses a configured rating and falls back for an invalid one', async () => {
    const { service, calls } = build(() => jsonResponse({ data: [] }), {
      GIPHY_API_KEY: 'k',
      GIPHY_RATING: 'r',
    });
    await service.trending(0);
    expect(calls[0]).toContain('rating=r');
    const { service: s2, calls: c2 } = build(() => jsonResponse({ data: [] }), {
      GIPHY_API_KEY: 'k',
      GIPHY_RATING: 'bogus',
    });
    await s2.trending(0);
    expect(c2[0]).toContain('rating=pg-13');
  });

  it('serves media for a remembered GIF and caches the bytes', async () => {
    const { service, calls } = build((url) =>
      url.includes('api.giphy.com')
        ? jsonResponse({ data: [gif('abcde12345')], pagination: { total_count: 1 } })
        : binaryResponse(1234),
    );
    await service.search('love', 0);
    const first = await service.getMedia('abcde12345', 'preview');
    expect(first.buffer.length).toBe(1234);
    expect(first.contentType).toBe('image/gif');
    await service.getMedia('abcde12345', 'preview');
    const mediaCalls = calls.filter((c) => c.includes('100w.gif'));
    expect(mediaCalls).toHaveLength(1);
    await service.getMedia('abcde12345', 'full');
    expect(calls.some((c) => c.includes('giphy-downsized.gif'))).toBe(true);
  });

  it('looks up a GIF it has not seen before through the single-gif endpoint', async () => {
    const { service, calls } = build((url) =>
      url.includes('api.giphy.com')
        ? jsonResponse({ data: gif('fresh12345') })
        : binaryResponse(500),
    );
    const media = await service.getMedia('fresh12345', 'full');
    expect(media.buffer.length).toBe(500);
    expect(calls[0]).toContain('/gifs/fresh12345?');
  });

  it('rejects a malformed id before touching the network', async () => {
    const { service, calls } = build(() => jsonResponse({}));
    await expect(service.getMedia('../etc/passwd', 'full')).rejects.toBeInstanceOf(
      NotFoundException,
    );
    await expect(service.getMedia('a b', 'full')).rejects.toBeInstanceOf(NotFoundException);
    expect(calls).toHaveLength(0);
  });

  it('refuses to download from a host that is not giphy.com', async () => {
    const { service, calls } = build((url) =>
      url.includes('api.giphy.com')
        ? jsonResponse({
            data: [gif('evil123456', 'evil.example.com')],
            pagination: { total_count: 1 },
          })
        : binaryResponse(10),
    );
    await service.search('love', 0);
    await expect(service.getMedia('evil123456', 'preview')).rejects.toBeInstanceOf(
      BadGatewayException,
    );
    expect(calls.some((c) => c.includes('evil.example.com'))).toBe(false);
  });

  it('rejects non-image and oversized responses', async () => {
    const seed = () => jsonResponse({ data: [gif('abcde12345')], pagination: { total_count: 1 } });
    const { service } = build((url) =>
      url.includes('api.giphy.com') ? seed() : binaryResponse(10, 'text/html'),
    );
    await service.search('love', 0);
    await expect(service.getMedia('abcde12345', 'preview')).rejects.toBeInstanceOf(
      BadGatewayException,
    );

    const { service: big } = build((url) =>
      url.includes('api.giphy.com')
        ? seed()
        : binaryResponse(10, 'image/gif', String(10 * 1024 * 1024)),
    );
    await big.search('love', 0);
    await expect(big.getMedia('abcde12345', 'preview')).rejects.toBeInstanceOf(BadGatewayException);
  });

  it('maps provider 429 to 503 and other failures to 502, without leaking the key', async () => {
    const { service } = build(() => jsonResponse({}, 429));
    await expect(service.search('love', 0)).rejects.toBeInstanceOf(ServiceUnavailableException);
    const { service: s2 } = build(() => jsonResponse({}, 500));
    await expect(s2.search('love', 0)).rejects.toBeInstanceOf(BadGatewayException);
    const failing = new GifsService(
      { get: () => 'secret-key' } as unknown as ConfigService,
      async () => {
        throw new Error('boom secret-key');
      },
    );
    await expect(failing.search('love', 0)).rejects.toMatchObject({
      message: 'GIF provider is unreachable',
    });
  });

  it('answers 404 for a GIF Giphy does not know', async () => {
    const { service } = build(() => jsonResponse({}, 404));
    await expect(service.getMedia('gone1234567', 'full')).rejects.toBeInstanceOf(NotFoundException);
  });
});
