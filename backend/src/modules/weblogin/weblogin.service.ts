import { BadRequestException, GoneException, Inject, Injectable, NotFoundException, UnauthorizedException } from '@nestjs/common';
import { Pool } from 'pg';
import { createHash, randomBytes, timingSafeEqual } from 'crypto';
import { PG_POOL } from '../../database/database.module';
import { TokenService } from '../sessions/token.service';
import { SessionsRepository } from '../sessions/sessions.repository';

const TTL_MS = 120_000;
const sha = (v: string) => createHash('sha256').update(v).digest('hex');
const same = (a: string, b: string) => {
  const x = Buffer.from(a, 'hex');
  const y = Buffer.from(b, 'hex');
  return x.length === y.length && timingSafeEqual(x, y);
};

/** Reduces a User-Agent to a short, safe, human label ("Chrome on Windows") for the phone's approval screen. */
export function describeClient(ua: string | undefined): { platform: 'web' | 'desktop'; name: string } {
  const s = ua ?? '';
  const browser = /Edg\//.test(s) ? 'Edge' : /OPR\//.test(s) ? 'Opera' : /Firefox\//.test(s) ? 'Firefox' : /Chrome\//.test(s) ? 'Chrome' : /Safari\//.test(s) ? 'Safari' : 'Browser';
  const os = /Windows/.test(s) ? 'Windows' : /Mac OS X|Macintosh/.test(s) ? 'macOS' : /Android/.test(s) ? 'Android' : /iPhone|iPad/.test(s) ? 'iOS' : /Linux/.test(s) ? 'Linux' : 'Unknown OS';
  return { platform: 'web', name: `${browser} on ${os}` };
}

@Injectable()
export class WebLoginService {
  constructor(
    @Inject(PG_POOL) private readonly pool: Pool,
    private readonly tokens: TokenService,
    private readonly sessions: SessionsRepository,
  ) {}

  async start(userAgent: string | undefined) {
    const { platform, name } = describeClient(userAgent);
    const secret = randomBytes(32).toString('base64url');
    const nonce = randomBytes(16).toString('base64url');
    await this.pool.query("DELETE FROM web_login_challenges WHERE expires_at < now() - interval '1 hour'");
    const { rows } = await this.pool.query<{ id: string }>(
      `INSERT INTO web_login_challenges (secret_hash, nonce_hash, platform, device_name, expires_at)
       VALUES ($1,$2,$3,$4,$5) RETURNING id`,
      [sha(secret), sha(nonce), platform, name, new Date(Date.now() + TTL_MS)],
    );
    const qrPayload = `mench://link/${rows[0].id}.${nonce}`;
    // The nonce exists only here (only its hash is stored), so the QR image is rendered now.
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const QRCode = require('qrcode');
    const qrSvg: string = await QRCode.toString(qrPayload, { type: 'svg', margin: 1, errorCorrectionLevel: 'M' });
    return { id: rows[0].id, secret, qrSvg, expiresInSeconds: TTL_MS / 1000 };
  }

  private async load(id: string) {
    const { rows } = await this.pool.query('SELECT * FROM web_login_challenges WHERE id = $1', [id]);
    const c = rows[0];
    if (!c) throw new NotFoundException('Unknown login request');
    return c;
  }

  /** What the phone shows BEFORE the user confirms: which device/platform is asking. */
  async describe(id: string, nonce: string) {
    const c = await this.load(id);
    if (!same(sha(nonce), c.nonce_hash)) throw new NotFoundException('Unknown login request');
    if (c.status !== 'pending' || new Date(c.expires_at).getTime() < Date.now()) throw new GoneException('This login request expired');
    return { platform: c.platform, deviceName: c.device_name, requestedAt: c.created_at };
  }

  async approve(id: string, nonce: string, userId: string, approve: boolean) {
    const c = await this.load(id);
    if (!same(sha(nonce), c.nonce_hash)) throw new NotFoundException('Unknown login request');
    if (c.status !== 'pending' || new Date(c.expires_at).getTime() < Date.now()) throw new GoneException('This login request expired');
    await this.pool.query('UPDATE web_login_challenges SET status = $2, user_id = $3 WHERE id = $1 AND status = \'pending\'', [id, approve ? 'approved' : 'rejected', userId]);
  }

  /** Polled by the web page. Returns tokens exactly once, only to whoever holds the secret. */
  async claim(id: string, secret: string) {
    const c = await this.load(id);
    if (!same(sha(secret), c.secret_hash)) throw new UnauthorizedException();
    if (new Date(c.expires_at).getTime() < Date.now() && c.status === 'pending') throw new GoneException('Expired');
    if (c.status === 'rejected') throw new GoneException('Rejected');
    if (c.status === 'consumed') throw new GoneException('Already used');
    if (c.status === 'pending') return { status: 'pending' as const };

    // Atomic single-use: only one claim can flip approved -> consumed.
    const flip = await this.pool.query("UPDATE web_login_challenges SET status = 'consumed' WHERE id = $1 AND status = 'approved' RETURNING user_id", [id]);
    if (!flip.rows[0]) throw new GoneException('Already used');
    const userId = flip.rows[0].user_id as string;
    const device = await this.pool.query<{ id: string }>(
      "INSERT INTO devices (user_id, device_name, platform) VALUES ($1,$2,$3) RETURNING id",
      [userId, c.device_name, c.platform],
    );
    const refreshToken = this.tokens.generateRefreshToken();
    const session = await this.sessions.create(userId, device.rows[0].id, this.tokens.hashRefreshToken(refreshToken), this.tokens.refreshTokenExpiry());
    const accessToken = this.tokens.signAccessToken({ sub: userId, deviceId: device.rows[0].id });
    return { status: 'approved' as const, accessToken, refreshToken, sessionId: session.id, userId, deviceId: device.rows[0].id };
  }
}
