import { BadGatewayException, BadRequestException, Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { FetchLike } from '../gifs/gifs.service';

export interface Tile {
  buffer: Buffer;
  contentType: string;
}

const MIN_ZOOM = 3;
const MAX_ZOOM = 18;
const MAX_TILE_BYTES = 512 * 1024;
const CACHE_MAX_BYTES = 24 * 1024 * 1024;
const TIMEOUT_MS = 6000;
const DEFAULT_TEMPLATE = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

/**
 * Map-tile proxy for the location bubble's static preview. Phones fetch tiles from this server
 * only (works on filtered networks, no third-party contact, tiles shared between both phones via
 * the cache). Coordinates are integers validated against the zoom level; the upstream URL is built
 * only from the configured template, so there is nothing for a caller to redirect (no SSRF).
 *
 * OpenStreetMap's tile policy asks for an identifying User-Agent and light use — fine for a
 * two-person messenger; point MAP_TILE_URL_TEMPLATE at your own provider for anything bigger.
 */
@Injectable()
export class MapsService {
  private readonly logger = new Logger('MapsService');
  private readonly cache = new Map<string, Tile>();
  private cacheBytes = 0;

  constructor(
    private readonly config: ConfigService,
    private readonly fetcher: FetchLike = fetch as unknown as FetchLike,
  ) {}

  async getTile(z: number, x: number, y: number): Promise<Tile> {
    if (![z, x, y].every(Number.isInteger))
      throw new BadRequestException('Tile coordinates must be integers');
    if (z < MIN_ZOOM || z > MAX_ZOOM)
      throw new BadRequestException(`Zoom must be between ${MIN_ZOOM} and ${MAX_ZOOM}`);
    const max = 2 ** z;
    if (x < 0 || y < 0 || x >= max || y >= max)
      throw new BadRequestException('Tile is outside the map');

    const key = `${z}/${x}/${y}`;
    const hit = this.cache.get(key);
    if (hit) {
      this.cache.delete(key);
      this.cache.set(key, hit);
      return hit;
    }

    const template = this.config.get<string>('MAP_TILE_URL_TEMPLATE') || DEFAULT_TEMPLATE;
    const url = template
      .replace('{z}', String(z))
      .replace('{x}', String(x))
      .replace('{y}', String(y));
    const userAgent =
      this.config.get<string>('MAP_TILE_USER_AGENT') || 'MENCH-private-messenger/1.0';
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const response = await (
        this.fetcher as unknown as (u: string, i: unknown) => ReturnType<FetchLike>
      )(url, {
        signal: controller.signal,
        redirect: 'error',
        headers: { 'User-Agent': userAgent },
      });
      if (!response.ok) throw new BadGatewayException(`Map provider error (${response.status})`);
      const contentType = (response.headers.get('content-type') ?? '').split(';')[0].trim();
      if (!contentType.startsWith('image/'))
        throw new BadGatewayException('Map provider returned a non-image');
      const buffer = Buffer.from(await response.arrayBuffer());
      if (buffer.length === 0 || buffer.length > MAX_TILE_BYTES)
        throw new BadGatewayException('Unexpected tile size');
      const tile = { buffer, contentType };
      this.store(key, tile);
      return tile;
    } catch (err) {
      if (err instanceof BadGatewayException) throw err;
      this.logger.warn(`Tile fetch failed: ${(err as Error).name}`);
      throw new BadGatewayException('Map provider is unreachable');
    } finally {
      clearTimeout(timer);
    }
  }

  private store(key: string, tile: Tile): void {
    this.cache.set(key, tile);
    this.cacheBytes += tile.buffer.length;
    while (this.cacheBytes > CACHE_MAX_BYTES && this.cache.size > 1) {
      const oldestKey = this.cache.keys().next().value as string;
      this.cacheBytes -= this.cache.get(oldestKey)?.buffer.length ?? 0;
      this.cache.delete(oldestKey);
    }
  }
}
