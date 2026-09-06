import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as jwt from 'jsonwebtoken';
import * as crypto from 'crypto';

export interface AccessTokenClaims {
  sub: string; // user id
  deviceId: string;
}

/**
 * Handles both token families:
 *  - short-lived signed JWT access tokens (stateless, verified via signature)
 *  - long-lived opaque refresh tokens (random 384-bit values; only a keyed
 *    hash is ever stored in Postgres, so a DB leak alone can't be replayed).
 *
 * The refresh token hash is HMAC-SHA256 keyed by JWT_REFRESH_SECRET, not
 * argon2. Argon2's per-call random salt makes it unsuitable for exact-match
 * DB lookup (WHERE refresh_token_hash = $1 would never match on re-hash).
 * That salting cost exists to slow brute-forcing of low-entropy secrets
 * (passwords); a 384-bit random refresh token has no such weakness, so a
 * fast, deterministic, keyed hash is both correct and standard practice
 * here.
 */
@Injectable()
export class TokenService {
  constructor(private readonly config: ConfigService) {}

  signAccessToken(claims: AccessTokenClaims): string {
    const secret = this.config.get<string>('JWT_ACCESS_SECRET') as string;
    const ttl = this.config.get<number>('JWT_ACCESS_TTL_SECONDS') as number;
    return jwt.sign(claims, secret, { expiresIn: ttl });
  }

  verifyAccessToken(token: string): AccessTokenClaims {
    const secret = this.config.get<string>('JWT_ACCESS_SECRET') as string;
    return jwt.verify(token, secret) as unknown as AccessTokenClaims;
  }

  generateRefreshToken(): string {
    return crypto.randomBytes(48).toString('base64url');
  }

  /** Deterministic keyed hash — safe for DB-indexed exact-match lookup. */
  hashRefreshToken(token: string): string {
    const secret = this.config.get<string>('JWT_REFRESH_SECRET') as string;
    return crypto.createHmac('sha256', secret).update(token).digest('hex');
  }

  /** Constant-time comparison to avoid timing side-channels on lookup confirmation. */
  refreshTokenMatchesHash(token: string, hash: string): boolean {
    const candidate = Buffer.from(this.hashRefreshToken(token), 'hex');
    const stored = Buffer.from(hash, 'hex');
    return candidate.length === stored.length && crypto.timingSafeEqual(candidate, stored);
  }

  refreshTokenExpiry(): Date {
    const ttl = this.config.get<number>('JWT_REFRESH_TTL_SECONDS') as number;
    return new Date(Date.now() + ttl * 1000);
  }
}
