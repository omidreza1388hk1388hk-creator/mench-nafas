import { BadGatewayException, BadRequestException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { MapsService } from './maps.service';

function build(handler: () => any, env: Record<string, string> = {}) {
  const calls: { url: string; init: any }[] = [];
  const fetcher = (async (url: string, init: any) => {
    calls.push({ url, init });
    return handler();
  }) as any;
  return { service: new MapsService({ get: (k: string) => env[k] } as unknown as ConfigService, fetcher), calls };
}

const png = (bytes = 100, type = 'image/png') => ({
  ok: true,
  status: 200,
  headers: { get: () => type },
  arrayBuffer: async () => new ArrayBuffer(bytes),
  json: async () => ({}),
});

describe('MapsService', () => {
  it('fetches a tile with the OSM template and an identifying User-Agent, then serves it from cache', async () => {
    const { service, calls } = build(() => png());
    const first = await service.getTile(15, 18000, 12000);
    expect(first.buffer.length).toBe(100);
    expect(calls[0].url).toBe('https://tile.openstreetmap.org/15/18000/12000.png');
    expect(calls[0].init.headers['User-Agent']).toContain('MENCH');
    expect(calls[0].init.redirect).toBe('error');
    await service.getTile(15, 18000, 12000);
    expect(calls).toHaveLength(1);
  });

  it('honours a custom template and user agent', async () => {
    const { service, calls } = build(() => png(), { MAP_TILE_URL_TEMPLATE: 'https://tiles.example.org/{z}/{x}/{y}.png', MAP_TILE_USER_AGENT: 'Mine/2' });
    await service.getTile(10, 5, 5);
    expect(calls[0].url).toBe('https://tiles.example.org/10/5/5.png');
    expect(calls[0].init.headers['User-Agent']).toBe('Mine/2');
  });

  it('validates zoom and tile bounds before any network call', async () => {
    const { service, calls } = build(() => png());
    await expect(service.getTile(2, 0, 0)).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.getTile(19, 0, 0)).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.getTile(5, 32, 0)).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.getTile(5, 0, -1)).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.getTile(5, 1.5, 0)).rejects.toBeInstanceOf(BadRequestException);
    expect(calls).toHaveLength(0);
  });

  it('rejects provider errors, non-images and oversized tiles', async () => {
    await expect(build(() => ({ ...png(), ok: false, status: 500 })).service.getTile(10, 1, 1)).rejects.toBeInstanceOf(BadGatewayException);
    await expect(build(() => png(10, 'text/html')).service.getTile(10, 1, 1)).rejects.toBeInstanceOf(BadGatewayException);
    await expect(build(() => png(600 * 1024)).service.getTile(10, 1, 1)).rejects.toBeInstanceOf(BadGatewayException);
    const failing = new MapsService({ get: () => undefined } as unknown as ConfigService, (async () => { throw new Error('down'); }) as any);
    await expect(failing.getTile(10, 1, 1)).rejects.toMatchObject({ message: 'Map provider is unreachable' });
  });
});
