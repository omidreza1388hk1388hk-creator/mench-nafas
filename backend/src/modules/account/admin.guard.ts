import { CanActivate, ExecutionContext, Injectable, NotFoundException, UnauthorizedException } from '@nestjs/common';
import { timingSafeEqual } from 'crypto';

function matches(given: string, expected: string | undefined): boolean {
  if (!expected || expected.length < 16) return false;
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

/**
 * Full admin: ADMIN_API_KEY only (rules, flags, announcements, Plus, referrals, sticker packs, stats).
 * If no admin key is configured the whole admin surface answers 404, so it cannot be probed.
 */
@Injectable()
export class AdminGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const adminKey = process.env.ADMIN_API_KEY;
    if (!adminKey || adminKey.length < 16) throw new NotFoundException();
    const req = context.switchToHttp().getRequest();
    if (!matches(String(req.headers['x-admin-key'] ?? ''), adminKey)) throw new UnauthorizedException('Bad admin key');
    req.adminRole = 'admin';
    return true;
  }
}

/**
 * Moderator: either MODERATOR_API_KEY (reports, appeals, restrictions, audit, user lookup) or the admin key.
 * A moderator key cannot touch rules, rewards, Plus or any configuration.
 */
@Injectable()
export class ModeratorGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const adminKey = process.env.ADMIN_API_KEY;
    const modKey = process.env.MODERATOR_API_KEY;
    if (!(adminKey && adminKey.length >= 16) && !(modKey && modKey.length >= 16)) throw new NotFoundException();
    const req = context.switchToHttp().getRequest();
    const given = String(req.headers['x-admin-key'] ?? '');
    if (matches(given, adminKey)) { req.adminRole = 'admin'; return true; }
    if (matches(given, modKey)) { req.adminRole = 'moderator'; return true; }
    throw new UnauthorizedException('Bad key');
  }
}
