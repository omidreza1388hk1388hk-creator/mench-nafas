import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { createHash } from 'crypto';
import { AccountRepository, PublicUserRow } from './account.repository';
import { SafetyService } from '../safety/safety.service';

const PUBLIC_LINK_BASE = process.env.PUBLIC_LINK_BASE ?? 'https://mench.app';

export function toPublicUser(u: PublicUserRow) {
  return {
    id: u.id,
    displayName: u.display_name,
    username: u.username,
    bio: u.bio,
    avatarVersion: u.avatar_version ? Number(u.avatar_version) : null,
    phoneE164: u.phone_e164,
  };
}

/** Stable 0..99 bucket per (user, flag) so a 20% rollout always hits the same 20% of users. */
function bucket(userId: string, key: string): number {
  const h = createHash('sha256').update(`${userId}:${key}`).digest();
  return h.readUInt32BE(0) % 100;
}

@Injectable()
export class AccountService {
  constructor(
    private readonly repo: AccountRepository,
    private readonly safety: SafetyService,
  ) {}

  async myInvite(userId: string) {
    const code = await this.repo.getOrCreateInviteCode(userId);
    return { code, link: `${PUBLIC_LINK_BASE}/i/${code}`, deepLink: `mench://invite/${code}` };
  }

  async resolveInvite(code: string) {
    const user = await this.repo.findByInviteCode(code);
    if (!user) throw new NotFoundException('Invite not found');
    return toPublicUser(user);
  }

  async byUsername(username: string) {
    const clean = username.replace(/^@/, '').trim();
    if (!/^[A-Za-z0-9_]{3,32}$/.test(clean)) throw new BadRequestException('Invalid username');
    const user = await this.repo.findByUsername(clean);
    if (!user) throw new NotFoundException('User not found');
    return toPublicUser(user);
  }

  async block(blockerId: string, blockedId: string) {
    if (blockerId === blockedId) throw new BadRequestException('Cannot block yourself');
    await this.repo.block(blockerId, blockedId);
  }

  unblock(blockerId: string, blockedId: string) {
    return this.repo.unblock(blockerId, blockedId);
  }

  async listBlocked(blockerId: string) {
    const rows = await this.repo.listBlocked(blockerId);
    return rows.map((r) => ({ ...toPublicUser(r), blockedAt: r.blocked_at.toISOString() }));
  }

  /**
   * Called by MessagesService before storing a message: the single "may this person send?" gate.
   * Covers blocks (Phase 1C) plus restrictions and automatic abuse rules (Phase 1D safety).
   */
  async assertNotBlocked(conversationId: string, senderId: string, body?: string | null) {
    if (await this.repo.isSenderBlockedInDirect(conversationId, senderId)) {
      throw new ForbiddenException('You cannot message this person');
    }
    await this.safety.assertCanSend(senderId, body);
  }

  exportData(userId: string) {
    return this.repo.exportData(userId);
  }

  async deleteAccount(userId: string, confirm: string) {
    if (confirm !== 'DELETE') throw new BadRequestException('Type DELETE to confirm');
    await this.repo.deleteAccount(userId);
  }

  async feedback(
    userId: string | null,
    dto: {
      category?: string;
      message: string;
      appVersion?: string;
      deviceInfo?: string;
      logs?: string;
    },
  ) {
    const message = (dto.message ?? '').trim();
    if (message.length < 3 || message.length > 4000)
      throw new BadRequestException('Message must be 3-4000 characters');
    await this.repo.addFeedback({
      userId,
      category: (dto.category ?? 'general').slice(0, 32),
      message,
      appVersion: dto.appVersion?.slice(0, 40),
      deviceInfo: dto.deviceInfo?.slice(0, 200),
      logs: dto.logs?.slice(0, 60_000),
    });
  }

  async remoteConfig(userId: string) {
    const [flags, announcements] = await Promise.all([
      this.repo.allFlags(),
      this.repo.activeAnnouncements(),
    ]);
    const resolved: Record<string, boolean> = {};
    for (const f of flags) resolved[f.key] = f.enabled && bucket(userId, f.key) < f.rollout_pct;
    return {
      flags: resolved,
      announcements,
      // Minimum/recommended build numbers drive the in-app update prompt (see AppUpdateChecker).
      update: {
        latestVersionCode: Number(process.env.APP_LATEST_VERSION_CODE ?? 0),
        minVersionCode: Number(process.env.APP_MIN_VERSION_CODE ?? 0),
        downloadUrl: process.env.APP_DOWNLOAD_URL ?? null,
      },
    };
  }

  private static readonly ALLOWED_EVENTS = new Set([
    'app_open',
    'onboarding_completed',
    'onboarding_skipped',
    'invite_shared',
    'qr_scanned',
    'message_sent',
    'call_started',
  ]);

  async countEvent(name: string) {
    if (!AccountService.ALLOWED_EVENTS.has(name)) throw new BadRequestException('Unknown event');
    await this.repo.countEvent(name);
  }
}
