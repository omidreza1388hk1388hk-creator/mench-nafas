import { BadRequestException, ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { UsersService } from '../users/users.service';
import { REALTIME_BROADCASTER, RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { NotificationsService } from '../notifications/notifications.service';
import { MemoriesRepository } from '../memories/memories.repository';
import { LetterRow, LettersRepository } from './letters.repository';

export interface LetterDto {
  id: string;
  conversationId: string;
  authorId: string;
  title: string;
  /** The author always sees the body; the recipient only after opening it. */
  body: string | null;
  unlockAt: string | null;
  openedAt: string | null;
  /** Optional photo; like the body, the recipient only gets it after opening the letter. */
  attachmentId: string | null;
  createdAt: string;
  isMine: boolean;
  /** Recipient-side: true when the letter is unsealed-able right now (unlock time reached, or none set). */
  canOpen: boolean;
}

function toDto(row: LetterRow, viewerId: string): LetterDto {
  const isMine = row.author_id === viewerId;
  const unlocked = !row.unlock_at || row.unlock_at.getTime() <= Date.now();
  return {
    id: row.id,
    conversationId: row.conversation_id,
    authorId: row.author_id,
    title: row.title,
    body: isMine || row.opened_at ? row.body : null,
    unlockAt: row.unlock_at ? row.unlock_at.toISOString() : null,
    openedAt: row.opened_at ? row.opened_at.toISOString() : null,
    attachmentId: isMine || row.opened_at ? row.attachment_id : null,
    createdAt: row.created_at.toISOString(),
    isMine,
    canOpen: !isMine && unlocked,
  };
}

@Injectable()
export class LettersService {
  constructor(
    private readonly lettersRepository: LettersRepository,
    private readonly conversationsRepository: ConversationsRepository,
    private readonly usersService: UsersService,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
    private readonly notificationsService: NotificationsService,
    private readonly memoriesRepository: MemoriesRepository,
  ) {}

  async create(
    conversationId: string,
    userId: string,
    dto: { title: string; body: string; unlockAt?: string; attachmentId?: string },
  ): Promise<LetterDto> {
    await this.assertDirectMember(conversationId, userId);
    if (dto.attachmentId && !(await this.memoriesRepository.isImageAttachmentOfConversation(dto.attachmentId, conversationId))) {
      throw new BadRequestException('attachmentId must be an image uploaded to this conversation');
    }
    const row = await this.lettersRepository.create(
      conversationId,
      userId,
      dto.title.trim(),
      dto.body,
      dto.unlockAt ?? null,
      dto.attachmentId ?? null,
    );

    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, {
      type: 'letter.received',
      conversationId,
      letterId: row.id,
    });
    const muted = new Set(await this.conversationsRepository.listMutedMemberIds(conversationId));
    const sender = await this.usersService.findById(userId);
    void this.notificationsService.notifyLetterReceived({
      recipientUserIds: otherIds.filter((id) => !muted.has(id)),
      conversationId,
      senderDisplayName: sender?.display_name ?? sender?.phone_e164 ?? 'Someone',
    });
    return toDto(row, userId);
  }

  async list(conversationId: string, userId: string): Promise<LetterDto[]> {
    await this.assertDirectMember(conversationId, userId);
    const rows = await this.lettersRepository.listForConversation(conversationId);
    return rows.map((r) => toDto(r, userId));
  }

  async open(conversationId: string, letterId: string, userId: string): Promise<LetterDto> {
    await this.assertDirectMember(conversationId, userId);
    const letter = await this.lettersRepository.findById(letterId);
    if (!letter || letter.conversation_id !== conversationId) {
      throw new NotFoundException('Letter not found');
    }
    if (letter.author_id === userId) {
      throw new BadRequestException('You wrote this letter; it is already visible to you');
    }
    if (letter.unlock_at && letter.unlock_at.getTime() > Date.now()) {
      throw new ForbiddenException('This letter is not ready to open yet');
    }
    const wasOpen = letter.opened_at !== null;
    const row = await this.lettersRepository.markOpened(letterId);
    if (!wasOpen) {
      this.realtimeBroadcaster.broadcastToUsers([letter.author_id], {
        type: 'letter.opened',
        conversationId,
        letterId,
      });
    }
    return toDto(row, userId);
  }

  private async assertDirectMember(conversationId: string, userId: string): Promise<void> {
    if (!(await this.conversationsRepository.isMember(conversationId, userId))) {
      throw new ForbiddenException('Not a member of this conversation');
    }
    if (await this.conversationsRepository.isGroup(conversationId)) {
      throw new BadRequestException('Letters are only available in direct conversations');
    }
  }
}
