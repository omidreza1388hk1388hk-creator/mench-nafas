import { BadRequestException, Injectable, Logger } from '@nestjs/common';
import * as dns from 'dns';
import * as http from 'http';
import * as https from 'https';
import * as net from 'net';

export interface LinkPreview {
  url: string;
  title: string | null;
  description: string | null;
  siteName: string | null;
}

const MAX_BYTES = 512 * 1024;
const TIMEOUT_MS = 5000;
const MAX_REDIRECTS = 3;
const CACHE_TTL_MS = 6 * 60 * 60 * 1000;
const CACHE_MAX = 500;

/**
 * Fetches a page ON THE SERVER and extracts its title/description, so phones
 * never contact the linked site (which also dodges filtering of foreign sites on the
 * device's network) and the sender's IP isn't exposed to it.
 *
 * It is an SSRF-sensitive feature, so: http(s) only, the address is validated at
 * CONNECT time inside a custom DNS lookup (not before — that would be open to DNS
 * rebinding), private/loopback/link-local/metadata ranges are refused, redirects are
 * followed manually (max 3) and re-validated each hop, with a body-size cap and timeout.
 */
@Injectable()
export class LinkPreviewService {
  private readonly logger = new Logger('LinkPreviewService');
  private readonly cache = new Map<string, { at: number; value: LinkPreview | null }>();

  async preview(rawUrl: string): Promise<LinkPreview | null> {
    let url: URL;
    try {
      url = new URL(rawUrl);
    } catch {
      throw new BadRequestException('Invalid URL');
    }
    if (url.protocol !== 'http:' && url.protocol !== 'https:')
      throw new BadRequestException('Only http(s) links');
    if (url.username || url.password)
      throw new BadRequestException('Credentials in URLs are not allowed');

    const key = url.toString();
    const hit = this.cache.get(key);
    if (hit && Date.now() - hit.at < CACHE_TTL_MS) return hit.value;

    let value: LinkPreview | null = null;
    try {
      const html = await this.fetchHtml(url, 0);
      value = html === null ? null : this.parse(url, html);
    } catch (err) {
      this.logger.debug(`Preview failed for ${url.host}: ${(err as Error).message}`);
    }
    if (this.cache.size >= CACHE_MAX) this.cache.delete(this.cache.keys().next().value as string);
    this.cache.set(key, { at: Date.now(), value });
    return value;
  }

  private fetchHtml(url: URL, depth: number): Promise<string | null> {
    return new Promise((resolve, reject) => {
      const lib = url.protocol === 'https:' ? https : http;
      const req = lib.request(
        url,
        {
          method: 'GET',
          timeout: TIMEOUT_MS,
          headers: {
            'User-Agent': 'Mozilla/5.0 (compatible; MenchLinkPreview/1.0)',
            Accept: 'text/html,application/xhtml+xml',
            'Accept-Language': 'fa,en;q=0.8',
          },
          lookup: (hostname, options, callback) => {
            dns.lookup(hostname, options as dns.LookupOneOptions, (err, address, family) => {
              if (err) return callback(err, address as any, family as any);
              if (this.isForbiddenAddress(String(address))) {
                return callback(
                  new Error('Address not allowed') as NodeJS.ErrnoException,
                  '' as any,
                  4 as any,
                );
              }
              return callback(null, address as any, family as any);
            });
          },
        },
        (res) => {
          const status = res.statusCode ?? 0;
          if (status >= 300 && status < 400 && res.headers.location) {
            res.resume();
            if (depth >= MAX_REDIRECTS) return reject(new Error('Too many redirects'));
            try {
              const next = new URL(res.headers.location, url);
              if (next.protocol !== 'http:' && next.protocol !== 'https:')
                return reject(new Error('Bad redirect'));
              return resolve(this.fetchHtml(next, depth + 1));
            } catch {
              return reject(new Error('Bad redirect'));
            }
          }
          const type = String(res.headers['content-type'] ?? '');
          if (status !== 200 || !/text\/html|application\/xhtml/i.test(type)) {
            res.resume();
            return resolve(null);
          }
          const chunks: Buffer[] = [];
          let size = 0;
          res.on('data', (chunk: Buffer) => {
            size += chunk.length;
            chunks.push(chunk);
            if (size >= MAX_BYTES) {
              res.destroy();
            }
          });
          res.on('close', () => resolve(Buffer.concat(chunks).toString('utf8')));
          res.on('error', reject);
        },
      );
      req.on('timeout', () => req.destroy(new Error('Timeout')));
      req.on('error', reject);
      req.end();
    });
  }

  private isForbiddenAddress(address: string): boolean {
    if (net.isIPv4(address)) {
      const [a, b] = address.split('.').map(Number);
      return (
        a === 0 ||
        a === 10 ||
        a === 127 ||
        (a === 100 && b >= 64 && b <= 127) || // CGNAT
        (a === 169 && b === 254) || // link-local + cloud metadata
        (a === 172 && b >= 16 && b <= 31) ||
        (a === 192 && b === 168) ||
        a >= 224 // multicast / reserved
      );
    }
    if (net.isIPv6(address)) {
      const lower = address.toLowerCase();
      if (lower === '::1' || lower === '::') return true;
      if (lower.startsWith('fe80') || lower.startsWith('fc') || lower.startsWith('fd')) return true;
      const mapped = lower.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
      if (mapped) return this.isForbiddenAddress(mapped[1]);
    }
    return false;
  }

  private parse(url: URL, html: string): LinkPreview | null {
    const head = html.slice(0, MAX_BYTES);
    const meta = (property: string): string | null => {
      const re = new RegExp(
        `<meta[^>]+(?:property|name)=["']${property}["'][^>]*content=["']([^"']*)["']|<meta[^>]+content=["']([^"']*)["'][^>]*(?:property|name)=["']${property}["']`,
        'i',
      );
      const match = head.match(re);
      return this.clean(match ? (match[1] ?? match[2]) : null);
    };
    const titleTag = this.clean(head.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] ?? null);
    const title = meta('og:title') ?? meta('twitter:title') ?? titleTag;
    const description =
      meta('og:description') ?? meta('twitter:description') ?? meta('description');
    if (!title && !description) return null;
    return {
      url: url.toString(),
      title: title ? title.slice(0, 160) : null,
      description: description ? description.slice(0, 280) : null,
      siteName: meta('og:site_name') ?? url.hostname.replace(/^www\./, ''),
    };
  }

  private clean(text: string | null): string | null {
    if (!text) return null;
    const decoded = text
      .replace(/&amp;/g, '&')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&#39;|&#x27;/g, "'")
      .replace(/\s+/g, ' ')
      .trim();
    return decoded || null;
  }
}
