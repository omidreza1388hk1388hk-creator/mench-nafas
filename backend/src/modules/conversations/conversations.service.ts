import { BadRequestException, ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { ConversationsRepository } from './conversations.repository';
import { UsersService } from '../users/users.service';
import { ConversationDto, ConversationMemberDto, toConversationDto, toConversationMemberDto } from './conversation.dto';
import { REALTIME_BROADCASTER, RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { NotificationsService } from '../notifications/notifications.service';
import { allowed, canRemoveMember, DEFAULT_GROUP_SETTINGS, toRole } from './group-policy';

@Injectable()
export class ConversationsService {
  constructor(
    private readonly conversationsRepository: ConversationsRepository,
    private readonly usersService: UsersService,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
    private readonly notificationsService: NotificationsService,
  ) {}

  /**
   * Idempotent: calling this twice for the same pair of users returns the
   * same conversation rather than creating a duplicate. This is a
   * check-then-create (not a single atomic DB constraint) — under Phase
   * 2's expected load a race between two near-simultaneous first messages
   * is rare and, if it ever did happen, just produces two direct
   * conversations for the same pair rather than data corruption. Real
   * uniqueness enforcement (a partial unique index over sorted member
   * pairs) is a reasonable Phase 3+ hardening item, noted here rather than
   * silently assumed solved.
   */
  async createDirect(requestingUserId: string, targetUserId: string): Promise<ConversationDto> {
    if (requestingUserId === targetUserId) {
      throw new BadRequestException('Cannot start a conversation with yourself');
    }

    const targetUser = await this.usersService.findById(targetUserId);
    if (!targetUser) {
      throw new NotFoundException('Target user not found');
    }

    const existing = await this.conversationsRepository.findDirectBetween(
      requestingUserId,
      targetUserId,
    );
    if (existing) {
      const otherMember = await this.conversationsRepository.getOtherMember(existing.id, requestingUserId);
      return toConversationDto(existing, otherMember);
    }

    const created = await this.conversationsRepository.createDirect(requestingUserId, targetUserId);
    const otherMember = await this.conversationsRepository.getOtherMember(created.id, requestingUserId);
    return toConversationDto(created, otherMember);
  }

  /** Get-or-create the user's private Saved messages conversation. Race-safe: a lost create race re-reads the winner. */
  async getOrCreateSaved(userId: string): Promise<ConversationDto> {
    const existing = await this.conversationsRepository.findSavedFor(userId);
    if (existing) return toConversationDto(existing, null);
    try {
      await this.conversationsRepository.createSaved(userId);
    } catch (err) {
      const again = await this.conversationsRepository.findSavedFor(userId);
      if (!again) throw err;
    }
    const created = await this.conversationsRepository.findSavedFor(userId);
    if (!created) throw new NotFoundException('Saved messages unavailable');
    return toConversationDto(created, null);
  }

  async listForUser(userId: string): Promise<ConversationDto[]> {
    const rows = await this.conversationsRepository.listForUser(userId);
    const otherMembers = await this.conversationsRepository.listOtherMembersForUser(userId);
    return rows.map((row) => toConversationDto(row, otherMembers.get(row.id) ?? null));
  }

  /** Per-user mute/pin — touches only the caller's own membership row. */
  async updateSettings(
    conversationId: string,
    userId: string,
    settings: { isMuted?: boolean; isPinned?: boolean; isArchived?: boolean },
  ): Promise<void> {
    await this.assertMember(conversationId, userId);
    await this.conversationsRepository.updateMemberSettings(conversationId, userId, settings);
  }

  /** Phase 9: either person in a direct chat can set the shared dates; both phones get the change live. */
  async updateUs(
    conversationId: string,
    userId: string,
    dates: { togetherSince?: string | null; nextMeetDate?: string | null },
  ): Promise<void> {
    await this.assertDirect(conversationId, userId);
    await this.conversationsRepository.updateUsDates(conversationId, dates);
    const conversation = await this.conversationsRepository.findById(conversationId);
    const memberIds = await this.conversationsRepository.listMemberIds(conversationId);
    this.realtimeBroadcaster.broadcastToUsers(memberIds, {
      type: 'us.updated',
      conversationId,
      togetherSince: conversation?.together_since ?? null,
      nextMeetDate: conversation?.next_meet_date ?? null,
    });
  }

  /** Phase 11: pin/unpin a message (any member). Everyone, including this user's other devices, is told live. */
  async setPinnedMessage(conversationId: string, userId: string, messageId: string | null): Promise<void> {
    await this.assertMember(conversationId, userId);
    if (await this.conversationsRepository.isGroup(conversationId)) {
      await this.assertGroupPermission(conversationId, userId, 'pinMessages', 'Only admins can pin messages in this group');
    }
    if (messageId && !(await this.conversationsRepository.messageBelongsToConversation(messageId, conversationId))) {
      throw new BadRequestException('Message does not belong to this conversation');
    }
    await this.conversationsRepository.setPinnedMessage(conversationId, messageId);
    const memberIds = await this.conversationsRepository.listMemberIds(conversationId);
    this.realtimeBroadcaster.broadcastToUsers(memberIds, { type: 'pin.changed', conversationId, messageId });
  }

  /** Phase 9: today's mood. Either person sets their own; the partner's phone updates live. No push (it's ambient, not a ping). */
  async setMood(conversationId: string, userId: string, emoji: string, text?: string): Promise<void> {
    await this.assertDirect(conversationId, userId);
    const cleanText = text?.trim() || null;
    await this.conversationsRepository.setMood(conversationId, userId, emoji, cleanText);
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, {
      type: 'mood.changed',
      conversationId,
      userId,
      emoji,
      text: cleanText,
      setAt: new Date().toISOString(),
    });
  }

  async getMoods(conversationId: string, userId: string) {
    await this.assertDirect(conversationId, userId);
    const rows = await this.conversationsRepository.listMoods(conversationId);
    const toDto = (r: (typeof rows)[number] | undefined) =>
      r && r.mood_emoji && r.mood_set_at
        ? { emoji: r.mood_emoji, text: r.mood_text, setAt: r.mood_set_at.toISOString() }
        : null;
    return {
      mine: toDto(rows.find((r) => r.user_id === userId)),
      other: toDto(rows.find((r) => r.user_id !== userId)),
    };
  }

  /** Phase 9: the "I miss you" button. Live event for a connected partner, push otherwise (skipped if they muted the chat). */
  async sendMissYou(conversationId: string, userId: string): Promise<void> {
    await this.assertDirect(conversationId, userId);
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'miss_you', conversationId, from: userId });

    const muted = new Set(await this.conversationsRepository.listMutedMemberIds(conversationId));
    const sender = await this.usersService.findById(userId);
    void this.notificationsService.notifyMissYou({
      recipientUserIds: otherIds.filter((id) => !muted.has(id)),
      conversationId,
      senderDisplayName: sender?.display_name ?? sender?.phone_e164 ?? 'Someone',
    });
  }

  /**
   * Phase 3A: the press-and-hold heartbeat. Live only — no push, because a pulse
   * that arrives hours later is just noise. Returns whether anyone was online to
   * feel it, so the sender's phone can say so honestly.
   */
  async sendHeartbeat(conversationId: string, userId: string, durationMs: number): Promise<{ delivered: boolean }> {
    await this.assertDirect(conversationId, userId);
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'heartbeat', conversationId, from: userId, durationMs });
    const delivered = otherIds.some((id) => this.realtimeBroadcaster.isUserConnected(id));
    return { delivered };
  }

  /** Phase 3: Together mode. Direct chats only (it is a two-person feature); live only, never stored. */
  async sendTogether(
    conversationId: string,
    userId: string,
    input: { kind: 'start' | 'sync' | 'stop'; attachmentId?: string; positionMs: number; playing: boolean },
  ): Promise<void> {
    await this.assertDirect(conversationId, userId);
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, {
      type: 'together',
      conversationId,
      from: userId,
      kind: input.kind,
      attachmentId: input.attachmentId ?? null,
      positionMs: input.positionMs,
      playing: input.playing,
      sentAt: Date.now(),
    });
  }

  /** Phase 3A: a hand-picked full-screen effect. Live only, works in groups too (any member may send one). */
  async sendScreenEffect(conversationId: string, userId: string, effect: string): Promise<void> {
    await this.assertMember(conversationId, userId);
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'screen_effect', conversationId, from: userId, effect });
  }

  /** Phase 9: a hug or a kiss. Same delivery rules as sendMissYou — live event, else push (skipped if muted). */
  async sendAffection(conversationId: string, userId: string, kind: 'hug' | 'kiss'): Promise<void> {
    await this.assertDirect(conversationId, userId);
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'affection', conversationId, from: userId, kind });

    const muted = new Set(await this.conversationsRepository.listMutedMemberIds(conversationId));
    const sender = await this.usersService.findById(userId);
    void this.notificationsService.notifyAffection({
      recipientUserIds: otherIds.filter((id) => !muted.has(id)),
      conversationId,
      senderDisplayName: sender?.display_name ?? sender?.phone_e164 ?? 'Someone',
      kind,
    });
  }

  async assertMember(conversationId: string, userId: string): Promise<void> {
    const isMember = await this.conversationsRepository.isMember(conversationId, userId);
    if (!isMember) {
      // 404, not 403: a non-member should not be able to distinguish
      // "conversation doesn't exist" from "exists but I'm not in it" —
      // that distinction itself leaks information.
      throw new NotFoundException('Conversation not found');
    }
  }

  /**
   * memberIds is validated in one batched query (UsersService.findManyByIds)
   * rather than one findById per id — a group invite list is exactly the
   * kind of input where someone might paste in 20 ids, and this schema has
   * no other place enforcing that every one of them refers to a real user
   * before conversation_members' FK constraint would (a raw FK violation
   * would leak as an ugly 500, not a clean 400).
   */
  async createGroup(requestingUserId: string, title: string, memberIds: string[]): Promise<ConversationDto> {
    const trimmedTitle = title.trim();
    if (trimmedTitle.length === 0) {
      throw new BadRequestException('Group title cannot be empty');
    }

    const uniqueOtherMemberIds = [...new Set(memberIds)].filter((id) => id !== requestingUserId);
    if (uniqueOtherMemberIds.length === 0) {
      throw new BadRequestException('A group needs at least one other member');
    }

    const foundUsers = await this.usersService.findManyByIds(uniqueOtherMemberIds);
    if (foundUsers.length !== uniqueOtherMemberIds.length) {
      throw new NotFoundException('One or more members were not found');
    }

    const created = await this.conversationsRepository.createGroup(requestingUserId, trimmedTitle, uniqueOtherMemberIds);
    return toConversationDto(created, null);
  }

  async listMembers(conversationId: string, requestingUserId: string): Promise<ConversationMemberDto[]> {
    await this.assertMember(conversationId, requestingUserId);
    const rows = await this.conversationsRepository.listMembers(conversationId);
    return rows.map(toConversationMemberDto);
  }

  /** Anyone already in the group can add more members — matches common WhatsApp/Telegram-style group permissions, unlike removeMember below where removing someone ELSE requires being the owner. */
  async addMembers(conversationId: string, requestingUserId: string, memberIds: string[]): Promise<void> {
    await this.assertGroup(conversationId, requestingUserId);
    await this.assertGroupPermission(conversationId, requestingUserId, 'addMembers', 'Only admins can add members to this group');

    const uniqueIds = [...new Set(memberIds)];
    const foundUsers = await this.usersService.findManyByIds(uniqueIds);
    if (foundUsers.length !== uniqueIds.length) {
      throw new NotFoundException('One or more members were not found');
    }

    await this.conversationsRepository.addMembers(conversationId, uniqueIds);

    // Broadcast only after the DB write commits — never on a failed write.
    const conversation = await this.conversationsRepository.findById(conversationId);
    const currentMemberIds = await this.conversationsRepository.listMemberIds(conversationId);

    for (const newMemberId of uniqueIds) {
      // The full current member set (post-add), including the new
      // member(s) themselves — everyone remaining needs to know a
      // member joined.
      this.realtimeBroadcaster.broadcastToUsers(currentMemberIds, {
        type: 'group.member_added',
        conversationId,
        userId: newMemberId,
        addedBy: requestingUserId,
      });

      // The new member also needs the conversation itself synced into
      // their local cache — group.member_added alone tells existing
      // clients "update this row you already have", but a brand-new
      // member has no row to update yet.
      if (conversation) {
        this.realtimeBroadcaster.broadcastToUsers([newMemberId], {
          type: 'conversation.added',
          conversation: toConversationDto(conversation, null),
        });

        // Push, unlike the socket events above, is NOT conditional on
        // connection state — see NotificationsService.notifyAddedToGroup's
        // doc comment for why a newly-added member needs it regardless.
        const addedByUser = await this.usersService.findById(requestingUserId);
        void this.notificationsService.notifyAddedToGroup({
          recipientUserId: newMemberId,
          conversationId,
          conversationTitle: conversation.title ?? '',
          addedByDisplayName: addedByUser?.display_name ?? addedByUser?.phone_e164 ?? 'Someone',
        });
      }
    }
  }

  /**
   * Removing YOURSELF ("leave group") is always allowed. Removing someone
   * ELSE requires the 'owner' role — otherwise any member could kick any
   * other member, which is not the permission model this is meant to
   * have. Both paths share this one method rather than a separate
   * "leaveGroup" — the authorization check is the only thing that
   * differs, and duplicating the rest (assertGroup, the repository call)
   * for a "self" special case would just be two ways to do the same
   * DELETE.
   */
  async removeMember(conversationId: string, requestingUserId: string, targetUserId: string): Promise<void> {
    await this.assertGroup(conversationId, requestingUserId);

    const isSelfRemoval = targetUserId === requestingUserId;
    if (!isSelfRemoval) {
      // Phase 2A: owner removes anyone else; an admin removes plain members only (see group-policy.ts).
      const actorRole = toRole(await this.conversationsRepository.getRole(conversationId, requestingUserId));
      const targetRole = toRole(await this.conversationsRepository.getRole(conversationId, targetUserId));
      if (!canRemoveMember(actorRole, targetRole)) {
        throw new ForbiddenException('You are not allowed to remove this member');
      }
    }

    // Snapshot the member list BEFORE removing — the removed user must
    // receive this event too (once), so their own client can drop the
    // conversation locally. Broadcasting to the post-removal list would
    // never reach them.
    const memberIdsBeforeRemoval = await this.conversationsRepository.listMemberIds(conversationId);

    await this.conversationsRepository.removeMember(conversationId, targetUserId);

    this.realtimeBroadcaster.broadcastToUsers(
      memberIdsBeforeRemoval,
      isSelfRemoval
        ? { type: 'group.member_left', conversationId, userId: targetUserId }
        : { type: 'group.member_removed', conversationId, userId: targetUserId, removedBy: requestingUserId },
    );
  }

  async renameGroup(conversationId: string, requestingUserId: string, title: string): Promise<void> {
    await this.assertGroup(conversationId, requestingUserId);
    await this.assertGroupPermission(conversationId, requestingUserId, 'editInfo', 'Only admins can edit this group');
    const trimmedTitle = title.trim();
    if (trimmedTitle.length === 0) {
      throw new BadRequestException('Group title cannot be empty');
    }
    await this.conversationsRepository.updateTitle(conversationId, trimmedTitle);

    const currentMemberIds = await this.conversationsRepository.listMemberIds(conversationId);
    this.realtimeBroadcaster.broadcastToUsers(currentMemberIds, {
      type: 'group.renamed',
      conversationId,
      title: trimmedTitle,
      renamedBy: requestingUserId,
    });
  }

  /** assertMember plus a kind check — every group-only mutation (add/remove members, rename) needs both, so this is the one place that fetches the row to check kind rather than each call site re-fetching it. */
  private async assertDirect(conversationId: string, requestingUserId: string): Promise<void> {
    await this.assertMember(conversationId, requestingUserId);
    if (await this.conversationsRepository.isGroup(conversationId)) {
      throw new BadRequestException('This operation is only valid for direct conversations');
    }
  }

  /** Phase 2A: throws 403 unless the caller's role satisfies the group's [permission] setting. */
  async assertGroupPermission(
    conversationId: string,
    userId: string,
    permission: 'addMembers' | 'editInfo' | 'pinMessages',
    message: string,
  ): Promise<void> {
    const settings = (await this.conversationsRepository.getGroupSettings(conversationId)) ?? DEFAULT_GROUP_SETTINGS;
    const role = toRole(await this.conversationsRepository.getRole(conversationId, userId));
    if (!allowed(settings[permission], role)) throw new ForbiddenException(message);
  }

  private async assertGroup(conversationId: string, requestingUserId: string): Promise<void> {
    await this.assertMember(conversationId, requestingUserId);
    const isGroup = await this.conversationsRepository.isGroup(conversationId);
    if (!isGroup) {
      throw new BadRequestException('This operation is only valid for group conversations');
    }
  }
}
