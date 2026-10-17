import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { randomBytes } from 'crypto';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { ConversationsService } from '../conversations/conversations.service';
import { toConversationDto } from '../conversations/conversation.dto';
import {
  canChangeRole,
  DEFAULT_GROUP_SETTINGS,
  GroupSettings,
  inviteProblem,
  isAdmin,
  parseSettingsPatch,
  toRole,
} from '../conversations/group-policy';
import {
  REALTIME_BROADCASTER,
  RealtimeBroadcaster,
} from '../realtime/realtime-broadcaster.interface';
import { GroupAdminRepository, InviteRow } from './group-admin.repository';

export const MAX_ACTIVE_INVITES = 10;

export interface GroupInfoDto extends GroupSettings {
  /** The caller's own role in this group. */
  myRole: string;
  /** Broadcast mode: only admins can post. */
  isChannel: boolean;
}

export interface InviteDto {
  id: string;
  code: string;
  expiresAt: string | null;
  maxUses: number | null;
  uses: number;
  createdAt: string;
}

export interface InvitePreviewDto {
  valid: boolean;
  /** Why the code can't be used (null when valid). */
  problem: 'revoked' | 'expired' | 'used_up' | null;
  title: string | null;
  memberCount: number;
}

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no 0/O/1/I: easy to read aloud and type

/** 12 characters from a 32-letter alphabet = 60 bits from a CSPRNG (rejection-free: 256 % 32 == 0). */
export function generateInviteCode(bytes: Buffer = randomBytes(12)): string {
  let code = '';
  for (const b of bytes) code += CODE_ALPHABET[b % CODE_ALPHABET.length];
  return code;
}

export function normalizeInviteCode(raw: string): string {
  return raw
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
}

export function toInviteDto(row: InviteRow): InviteDto {
  return {
    id: row.id,
    code: row.code,
    expiresAt: row.expires_at ? row.expires_at.toISOString() : null,
    maxUses: row.max_uses,
    uses: row.uses,
    createdAt: row.created_at.toISOString(),
  };
}

@Injectable()
export class GroupAdminService {
  constructor(
    private readonly repository: GroupAdminRepository,
    private readonly conversationsRepository: ConversationsRepository,
    private readonly conversationsService: ConversationsService,
    @Inject(REALTIME_BROADCASTER) private readonly broadcaster: RealtimeBroadcaster,
    private readonly nowFn: () => number = () => Date.now(),
  ) {}

  private async requireGroupRole(conversationId: string, userId: string) {
    await this.conversationsService.assertMember(conversationId, userId);
    if (!(await this.conversationsRepository.isGroup(conversationId))) {
      throw new BadRequestException('This operation is only valid for group conversations');
    }
    return toRole(await this.conversationsRepository.getRole(conversationId, userId));
  }

  private async requireAdmin(conversationId: string, userId: string) {
    const role = await this.requireGroupRole(conversationId, userId);
    if (!isAdmin(role)) throw new ForbiddenException('Only admins can do this');
    return role;
  }

  async getInfo(conversationId: string, userId: string): Promise<GroupInfoDto> {
    const role = await this.requireGroupRole(conversationId, userId);
    const settings =
      (await this.conversationsRepository.getGroupSettings(conversationId)) ??
      DEFAULT_GROUP_SETTINGS;
    return { ...settings, myRole: role ?? 'member', isChannel: settings.sendMessages === 'admins' };
  }

  async updateSettings(
    conversationId: string,
    userId: string,
    input: Record<string, unknown>,
  ): Promise<GroupInfoDto> {
    await this.requireAdmin(conversationId, userId);
    const patch = parseSettingsPatch(input);
    if (!patch) throw new BadRequestException('No valid settings to change');
    await this.conversationsRepository.updateGroupSettings(conversationId, patch);
    return this.getInfo(conversationId, userId);
  }

  async changeRole(
    conversationId: string,
    actorId: string,
    targetUserId: string,
    newRole: string,
  ): Promise<void> {
    const actor = await this.requireGroupRole(conversationId, actorId);
    const target = toRole(await this.conversationsRepository.getRole(conversationId, targetUserId));
    if (target === null) throw new NotFoundException('That person is not in this group');
    if (!canChangeRole(actor, target, newRole))
      throw new ForbiddenException('Only the owner can change roles (not their own)');
    await this.conversationsRepository.setRole(
      conversationId,
      targetUserId,
      newRole as 'admin' | 'member',
    );
  }

  /** Only the current owner can hand the group over, and only to someone who is already a member. */
  async transferOwnership(
    conversationId: string,
    actorId: string,
    targetUserId: string,
  ): Promise<void> {
    const actor = await this.requireGroupRole(conversationId, actorId);
    if (actor !== 'owner') throw new ForbiddenException('Only the owner can transfer ownership');
    if (targetUserId === actorId) throw new BadRequestException('You already own this group');
    const target = toRole(await this.conversationsRepository.getRole(conversationId, targetUserId));
    if (target === null) throw new NotFoundException('That person is not in this group');
    await this.conversationsRepository.transferOwnership(conversationId, actorId, targetUserId);
  }

  async createInvite(
    conversationId: string,
    userId: string,
    input: { expiresInHours?: number; maxUses?: number },
  ): Promise<InviteDto> {
    await this.requireAdmin(conversationId, userId);
    if ((await this.repository.countActiveInvites(conversationId)) >= MAX_ACTIVE_INVITES) {
      throw new BadRequestException('Too many active invite links — revoke one first');
    }
    const expiresAt = input.expiresInHours
      ? new Date(this.nowFn() + input.expiresInHours * 3600_000)
      : null;
    const row = await this.repository.createInvite({
      conversationId,
      code: generateInviteCode(),
      createdBy: userId,
      expiresAt,
      maxUses: input.maxUses ?? null,
    });
    return toInviteDto(row);
  }

  async listInvites(conversationId: string, userId: string): Promise<InviteDto[]> {
    await this.requireAdmin(conversationId, userId);
    return (await this.repository.listInvites(conversationId)).map(toInviteDto);
  }

  async revokeInvite(conversationId: string, userId: string, inviteId: string): Promise<void> {
    await this.requireAdmin(conversationId, userId);
    if (!(await this.repository.revokeInvite(conversationId, inviteId)))
      throw new NotFoundException('Invite not found');
  }

  async preview(rawCode: string): Promise<InvitePreviewDto> {
    const invite = await this.repository.findByCode(normalizeInviteCode(rawCode));
    if (!invite) return { valid: false, problem: null, title: null, memberCount: 0 };
    const conversation = await this.conversationsRepository.findById(invite.conversation_id);
    const problem = this.problemOf(invite);
    return {
      valid: problem === null,
      problem,
      title: conversation?.title ?? null,
      memberCount: await this.repository.memberCount(invite.conversation_id),
    };
  }

  async join(rawCode: string, userId: string) {
    const invite = await this.repository.findByCode(normalizeInviteCode(rawCode));
    if (!invite) throw new NotFoundException('Invite not found');
    const problem = this.problemOf(invite);
    if (problem)
      throw new ConflictException(
        `This invite link is ${problem === 'used_up' ? 'used up' : problem}`,
      );

    const conversation = await this.conversationsRepository.findById(invite.conversation_id);
    if (!conversation) throw new NotFoundException('Group no longer exists');

    const already = await this.conversationsRepository.getRole(invite.conversation_id, userId);
    if (already) return toConversationDto(conversation, null); // idempotent: joining twice is fine and costs no use

    if (!(await this.repository.consumeUse(invite.id)))
      throw new ConflictException('This invite link is no longer valid');
    await this.conversationsRepository.addMembers(invite.conversation_id, [userId]);

    const memberIds = await this.conversationsRepository.listMemberIds(invite.conversation_id);
    this.broadcaster.broadcastToUsers(memberIds, {
      type: 'group.member_added',
      conversationId: invite.conversation_id,
      userId,
      addedBy: invite.created_by,
    });
    return toConversationDto(conversation, null);
  }

  private problemOf(invite: InviteRow) {
    return inviteProblem(
      {
        expiresAtMs: invite.expires_at ? invite.expires_at.getTime() : null,
        maxUses: invite.max_uses,
        uses: invite.uses,
        revoked: invite.revoked_at !== null,
      },
      this.nowFn(),
    );
  }
}
