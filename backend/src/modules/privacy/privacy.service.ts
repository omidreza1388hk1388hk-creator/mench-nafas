import { ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { PrivacyRepository } from './privacy.repository';
import {
  REALTIME_BROADCASTER,
  RealtimeBroadcaster,
} from '../realtime/realtime-broadcaster.interface';
import { DEFAULT_PRIVACY, PrivacySettings, normalizeDisappearSeconds } from './privacy.types';
import { UpdatePrivacyDto } from './dto/update-privacy.dto';

@Injectable()
export class PrivacyService {
  constructor(
    private readonly repository: PrivacyRepository,
    @Inject(REALTIME_BROADCASTER) private readonly broadcaster: RealtimeBroadcaster,
  ) {}

  get(userId: string): Promise<PrivacySettings> {
    return this.repository.get(userId);
  }

  async update(userId: string, patch: UpdatePrivacyDto): Promise<PrivacySettings> {
    const current = await this.repository.get(userId);
    const next: PrivacySettings = {
      lastSeen: patch.lastSeen ?? current.lastSeen,
      profilePhoto: patch.profilePhoto ?? current.profilePhoto,
      addMe: patch.addMe ?? current.addMe,
      calls: patch.calls ?? current.calls,
      readReceipts: patch.readReceipts ?? current.readReceipts,
    };
    await this.repository.save(userId, next);
    return next;
  }

  // --- enforcement helpers (used by users / calls / messages) ---------------

  /** Hidden "who can find me": looks exactly like "no such user" so existence is not leaked. */
  async assertDiscoverable(targetUserId: string, requesterId: string): Promise<void> {
    if (targetUserId === requesterId) return;
    if ((await this.repository.get(targetUserId)).addMe === 'nobody') {
      throw new NotFoundException('User not found');
    }
  }

  async assertPhotoVisible(ownerId: string, requesterId: string): Promise<void> {
    if (ownerId === requesterId) return;
    if ((await this.repository.get(ownerId)).profilePhoto === 'nobody') {
      throw new NotFoundException('Avatar not found');
    }
  }

  async assertCallable(calleeId: string): Promise<void> {
    if ((await this.repository.get(calleeId)).calls === 'nobody') {
      throw new ForbiddenException('This user does not accept calls');
    }
  }

  async sendsReadReceipts(userId: string): Promise<boolean> {
    return (await this.repository.get(userId)).readReceipts;
  }

  // --- disappearing messages -----------------------------------------------

  async getDisappearing(
    conversationId: string,
    userId: string,
  ): Promise<{ seconds: number | null }> {
    await this.requireMember(conversationId, userId);
    return { seconds: await this.repository.getDisappearSeconds(conversationId) };
  }

  /** Direct chats: either person may change it. Groups: only the owner. */
  async setDisappearing(
    conversationId: string,
    userId: string,
    seconds: number | undefined,
  ): Promise<{ seconds: number | null }> {
    const membership = await this.requireMember(conversationId, userId);
    if (membership.kind === 'group' && membership.role !== 'owner') {
      throw new ForbiddenException('Only the group owner can change disappearing messages');
    }
    const normalized = normalizeDisappearSeconds(seconds);
    await this.repository.setDisappearSeconds(conversationId, normalized);
    const members = await this.repository.memberIds(conversationId);
    this.broadcaster.broadcastToUsers(members, {
      type: 'conversation.disappearing_changed',
      conversationId,
      seconds: normalized,
      changedBy: userId,
    });
    return { seconds: normalized };
  }

  private async requireMember(conversationId: string, userId: string) {
    const membership = await this.repository.membership(conversationId, userId);
    // 404, not 403: a non-member must not learn that the conversation exists.
    if (!membership) throw new NotFoundException('Conversation not found');
    return membership;
  }
}

export { DEFAULT_PRIVACY };
