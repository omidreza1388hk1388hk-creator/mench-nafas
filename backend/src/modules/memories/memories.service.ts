import { BadRequestException, ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { REALTIME_BROADCASTER, RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { MemoriesRepository, MemoryRow } from './memories.repository';

export interface MemoryDto {
  id: string;
  conversationId: string;
  authorId: string;
  title: string;
  note: string | null;
  memoryDate: string;
  attachmentId: string | null;
  createdAt: string;
  isMine: boolean;
}

function toDto(row: MemoryRow, viewerId: string): MemoryDto {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    authorId: row.author_id,
    title: row.title,
    note: row.note,
    memoryDate: row.memory_date,
    attachmentId: row.attachment_id,
    createdAt: row.created_at.toISOString(),
    isMine: row.author_id === viewerId,
  };
}

@Injectable()
export class MemoriesService {
  constructor(
    private readonly memoriesRepository: MemoriesRepository,
    private readonly conversationsRepository: ConversationsRepository,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
  ) {}

  async list(conversationId: string, userId: string): Promise<MemoryDto[]> {
    await this.assertDirectMember(conversationId, userId);
    return (await this.memoriesRepository.list(conversationId)).map((r) => toDto(r, userId));
  }

  async create(
    conversationId: string,
    userId: string,
    dto: { title: string; note?: string; memoryDate: string; attachmentId?: string },
  ): Promise<MemoryDto> {
    await this.assertDirectMember(conversationId, userId);
    if (Number.isNaN(Date.parse(dto.memoryDate))) {
      throw new BadRequestException('Invalid memoryDate');
    }
    if (dto.attachmentId) {
      const ok = await this.memoriesRepository.isImageAttachmentOfConversation(dto.attachmentId, conversationId);
      if (!ok) throw new BadRequestException('attachmentId must be an image uploaded to this conversation');
    }
    const row = await this.memoriesRepository.create(conversationId, userId, {
      title: dto.title.trim(),
      note: dto.note?.trim() || null,
      memoryDate: dto.memoryDate,
      attachmentId: dto.attachmentId ?? null,
    });
    await this.notifyChanged(conversationId, userId);
    return toDto(row, userId);
  }

  async remove(conversationId: string, memoryId: string, userId: string): Promise<void> {
    await this.assertDirectMember(conversationId, userId);
    const memory = await this.memoriesRepository.findById(memoryId);
    if (!memory || memory.conversation_id !== conversationId) throw new NotFoundException('Memory not found');
    if (memory.author_id !== userId) throw new ForbiddenException('Only the author can delete a memory');
    await this.memoriesRepository.delete(memoryId);
    await this.notifyChanged(conversationId, userId);
  }

  private async notifyChanged(conversationId: string, userId: string): Promise<void> {
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'memory.changed', conversationId });
  }

  private async assertDirectMember(conversationId: string, userId: string): Promise<void> {
    if (!(await this.conversationsRepository.isMember(conversationId, userId))) {
      throw new ForbiddenException('Not a member of this conversation');
    }
    if (await this.conversationsRepository.isGroup(conversationId)) {
      throw new BadRequestException('Memories are only available in direct conversations');
    }
  }
}
