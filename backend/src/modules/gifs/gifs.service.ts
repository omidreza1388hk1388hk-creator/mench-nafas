import { BadGatewayException, BadRequestException, Injectable, Logger, NotFoundException, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

export interface GifItem {
  id: string;
  title: string;
  width: number;
  height: number;
}

export interface GifPage {
  items: GifItem[];
  /** Offset to request next, or null when the provider has no more results. */
  nextOffset: number | null;
}

export type GifSize = 'preview' | 'full';

export interface GifMedia {
  buffer: Buffer;
  contentType: string;
}

/** The fetch function shape this service needs; injectable so tests never touch the network. */
export type FetchLike = (
  url: string,
  init?: { signal?: AbortSignal; redirect?: 'error' | 'follow' | 'manual' },
) => Promise<{ ok: boolean; status: number; headers: { get(name: string): string | null }; json(): Promise<unknown>; arrayBuffer(): Promise<ArrayBuffer> }>;

export const GIF_FETCH = 'GIF_FETCH';

const GIPHY_API = 'https://api.giphy.com/v1/gifs';
const PAGE_SIZE = 24;
const MAX_QUERY_LENGTH = 60;
const MAX_OFFSET = 500; // Giphy's pagination ceiling is far above what a chat picker needs
const ID_PATTERN = /^[A-Za-z0-9]{5,32}$/;
const MAX_MEDIA_BYTES = 4 * 1024 * 1024;
const REQUEST_TIMEOUT_MS = 8000;
const URL_CACHE_MAX = 2000;
const URL_CACHE_TTL_MS = 6 * 60 * 60 * 1000;
const MEDIA_CACHE_MAX_BYTES = 48 * 1024 * 1024;

interface GiphyImage {
  url?: string;
  width?: string;
  height?: string;
}

interface GiphyGif {
  id?: string;
  title?: string;
  images?: Record<string, GiphyImage | undefined>;
}

interface UrlEntry {
  at: number;
  preview: string | null;
  full: string | null;
}

/**
 * GIF search + media proxy.
 *
 * Phones never talk to the GIF provider: this server does the search and also serves the media
 * bytes (GET /gifs/:id/media). That keeps the API key off the devices, works on networks that
 * filter foreign CDNs (same reasoning as link-preview), never leaks the app's bearer token to a
 * third party and means a message only has to carry the provider's GIF id.
 *
 * Giphy is the provider (Tenor's API was shut down on 2026-06-30). Keys come from GIPHY_API_KEY;
 * a beta key is rate limited and Giphy requires approval for production use.
 *
 * SSRF: media URLs are only ever taken from Giphy's own API responses and must be https on a
 * giphy.com host; ids are validated against a strict pattern before they reach any URL.
 */
@Injectable()
export class GifsService {
  private readonly logger = new Logger('GifsService');
  private readonly urls = new Map<string, UrlEntry>();
  private readonly media = new Map<string, GifMedia>(); // insertion order = LRU order
  private mediaBytes = 0;

  constructor(
    private readonly config: ConfigService,
    private readonly fetcher: FetchLike = fetch as unknown as FetchLike,
  ) {}

  private apiKey(): string {
    const key = this.config.get<string>('GIPHY_API_KEY');
    if (!key) throw new ServiceUnavailableException('GIF search is not configured on this server');
    return key;
  }

  private rating(): string {
    const rating = this.config.get<string>('GIPHY_RATING');
    return rating && ['g', 'pg', 'pg-13', 'r'].includes(rating) ? rating : 'pg-13';
  }

  async search(rawQuery: string, offset: number): Promise<GifPage> {
    const query = (rawQuery ?? '').trim();
    if (!query) throw new BadRequestException('Query is required');
    if (query.length > MAX_QUERY_LENGTH) throw new BadRequestException('Query is too long');
    const params = new URLSearchParams({ q: query, lang: 'en' });
    return this.list('search', params, offset);
  }

  trending(offset: number): Promise<GifPage> {
    return this.list('trending', new URLSearchParams(), offset);
  }

  private async list(path: 'search' | 'trending', params: URLSearchParams, rawOffset: number): Promise<GifPage> {
    const offset = Math.min(Math.max(Number.isFinite(rawOffset) ? Math.floor(rawOffset) : 0, 0), MAX_OFFSET);
    params.set('api_key', this.apiKey());
    params.set('limit', String(PAGE_SIZE));
    params.set('offset', String(offset));
    params.set('rating', this.rating());
    const body = (await this.getJson(`${GIPHY_API}/${path}?${params.toString()}`)) as {
      data?: GiphyGif[];
      pagination?: { total_count?: number; count?: number };
    };
    const items: GifItem[] = [];
    for (const gif of body.data ?? []) {
      const item = this.remember(gif);
      if (item) items.push(item);
    }
    const received = body.data?.length ?? 0;
    const total = body.pagination?.total_count ?? 0;
    const next = offset + received;
    return { items, nextOffset: received > 0 && next < total && next <= MAX_OFFSET ? next : null };
  }

  /** Records the preview/full URLs of a result (so /media never needs a second API call) and returns its public shape. */
  private remember(gif: GiphyGif): GifItem | null {
    if (!gif.id || !ID_PATTERN.test(gif.id)) return null;
    const preview = pickUrl(gif, ['fixed_width_small', 'fixed_width', 'downsized']);
    const full = pickUrl(gif, ['downsized', 'fixed_width', 'original']);
    if (!preview || !full) return null;
    if (this.urls.size >= URL_CACHE_MAX) this.urls.delete(this.urls.keys().next().value as string);
    this.urls.set(gif.id, { at: Date.now(), preview, full });
    const dims = gif.images?.['fixed_width'] ?? gif.images?.['downsized'] ?? gif.images?.['original'];
    const width = toDimension(dims?.width);
    const height = toDimension(dims?.height);
    return { id: gif.id, title: (gif.title ?? '').slice(0, 120), width, height };
  }

  async getMedia(id: string, size: GifSize): Promise<GifMedia> {
    if (!ID_PATTERN.test(id)) throw new NotFoundException('GIF not found');
    const cacheKey = `${id}:${size}`;
    const cached = this.media.get(cacheKey);
    if (cached) {
      this.media.delete(cacheKey); // refresh LRU position
      this.media.set(cacheKey, cached);
      return cached;
    }
    const url = await this.mediaUrl(id, size);
    const media = await this.download(url);
    this.storeMedia(cacheKey, media);
    return media;
  }

  private async mediaUrl(id: string, size: GifSize): Promise<string> {
    const hit = this.urls.get(id);
    if (hit && Date.now() - hit.at < URL_CACHE_TTL_MS) {
      const url = size === 'preview' ? hit.preview : hit.full;
      if (url) return url;
    }
    // Not seen by this process (restart, other instance, old message): ask Giphy for the single GIF.
    const params = new URLSearchParams({ api_key: this.apiKey() });
    const body = (await this.getJson(`${GIPHY_API}/${id}?${params.toString()}`, true)) as { data?: GiphyGif };
    if (!body.data || !this.remember({ ...body.data, id })) throw new NotFoundException('GIF not found');
    const entry = this.urls.get(id);
    const url = size === 'preview' ? entry?.preview : entry?.full;
    if (!url) throw new NotFoundException('GIF not found');
    return url;
  }

  private async download(rawUrl: string): Promise<GifMedia> {
    const url = new URL(rawUrl);
    if (url.protocol !== 'https:' || !isGiphyHost(url.hostname)) {
      this.logger.warn(`Refusing to fetch non-Giphy media host ${url.hostname}`);
      throw new BadGatewayException('Unexpected GIF host');
    }
    const response = await this.request(url.toString());
    const contentType = response.headers.get('content-type') ?? '';
    if (!contentType.startsWith('image/')) throw new BadGatewayException('GIF provider returned a non-image');
    const declared = Number(response.headers.get('content-length') ?? '0');
    if (declared > MAX_MEDIA_BYTES) throw new BadGatewayException('GIF is too large');
    const buffer = Buffer.from(await response.arrayBuffer());
    if (buffer.length === 0 || buffer.length > MAX_MEDIA_BYTES) throw new BadGatewayException('GIF is too large');
    return { buffer, contentType: contentType.split(';')[0].trim() };
  }

  private storeMedia(key: string, media: GifMedia): void {
    this.media.set(key, media);
    this.mediaBytes += media.buffer.length;
    while (this.mediaBytes > MEDIA_CACHE_MAX_BYTES && this.media.size > 1) {
      const oldestKey = this.media.keys().next().value as string;
      const oldest = this.media.get(oldestKey);
      this.media.delete(oldestKey);
      this.mediaBytes -= oldest?.buffer.length ?? 0;
    }
  }

  private async getJson(url: string, notFoundIsMissing = false): Promise<unknown> {
    const response = await this.request(url, notFoundIsMissing);
    try {
      return await response.json();
    } catch {
      throw new BadGatewayException('GIF provider sent an unreadable response');
    }
  }

  private async request(url: string, notFoundIsMissing = false) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const response = await this.fetcher(url, { signal: controller.signal, redirect: 'error' });
      if (response.status === 404 && notFoundIsMissing) throw new NotFoundException('GIF not found');
      if (response.status === 429) throw new ServiceUnavailableException('GIF provider rate limit reached, try again shortly');
      if (!response.ok) throw new BadGatewayException(`GIF provider error (${response.status})`);
      return response;
    } catch (err) {
      if (err instanceof NotFoundException || err instanceof BadGatewayException || err instanceof ServiceUnavailableException) throw err;
      // Never log the URL: it contains the API key.
      this.logger.warn(`GIF provider request failed: ${(err as Error).name}`);
      throw new BadGatewayException('GIF provider is unreachable');
    } finally {
      clearTimeout(timer);
    }
  }
}

function isGiphyHost(hostname: string): boolean {
  return hostname === 'giphy.com' || hostname.endsWith('.giphy.com');
}

function pickUrl(gif: GiphyGif, keys: string[]): string | null {
  for (const key of keys) {
    const url = gif.images?.[key]?.url;
    if (url) return url;
  }
  return null;
}

function toDimension(raw: string | undefined): number {
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 && n <= 4000 ? Math.round(n) : 200;
}
