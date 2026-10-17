import { BadRequestException, ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { REALTIME_BROADCASTER, RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { CounterRow, CountersRepository } from './counters.repository';

export const MAX_COUNTERS_PER_CONVERSATION = 20;

export interface CounterDto {
  id: string;
  conversationId: string;
  title: string;
  emoji: string;
  kind: 'date' | 'tally';
  anchorDate: string | null;
  tallyCount: number;
  theme: number;
  isMine: boolean;
}

function toDto(row: CounterRow, viewerId: string): CounterDto {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    title: row.title,
    emoji: row.emoji,
    kind: row.kind,
    anchorDate: row.anchor_date,
    tallyCount: row.tally_count,
    theme: row.theme ?? 0,
    isMine: row.author_id === viewerId,
  };
}

@Injectable()
export class CountersService {
  constructor(
    private readonly countersRepository: CountersRepository,
    private readonly conversationsRepository: ConversationsRepository,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
  ) {}

  async list(conversationId: string, userId: string): Promise<CounterDto[]> {
    await this.assertDirectMember(conversationId, userId);
    return (await this.countersRepository.list(conversationId)).map((r) => toDto(r, userId));
  }

  async create(
    conversationId: string,
    userId: string,
    dto: { title: string; emoji?: string; kind: 'date' | 'tally'; anchorDate?: string; theme?: number },
  ): Promise<CounterDto> {
    await this.assertDirectMember(conversationId, userId);
    if (dto.kind === 'date' && (!dto.anchorDate || Number.isNaN(Date.parse(dto.anchorDate)))) {
      throw new BadRequestException('A date counter needs a valid anchorDate');
    }
    if ((await this.countersRepository.count(conversationId)) >= MAX_COUNTERS_PER_CONVERSATION) {
      throw new BadRequestException(`At most ${MAX_COUNTERS_PER_CONVERSATION} counters per conversation`);
    }
    const row = await this.countersRepository.create(conversationId, userId, {
      title: dto.title.trim(),
      emoji: dto.emoji?.trim() || '💗',
      kind: dto.kind,
      anchorDate: dto.kind === 'date' ? dto.anchorDate! : null,
      theme: dto.theme ?? 0,
    });
    await this.notifyChanged(conversationId, userId);
    return toDto(row, userId);
  }

  /** Either person may bump a tally. */
  async bump(conversationId: string, counterId: string, userId: string, delta: 1 | -1): Promise<CounterDto> {
    await this.assertDirectMember(conversationId, userId);
    const existing = await this.countersRepository.findById(counterId);
    if (!existing || existing.conversation_id !== conversationId) throw new NotFoundException('Counter not found');
    if (existing.kind !== 'tally') throw new BadRequestException('Only tally counters can be bumped');
    const row = await this.countersRepository.bump(counterId, delta);
    if (!row) throw new NotFoundException('Counter not found');
    await this.notifyChanged(conversationId, userId);
    return toDto(row, userId);
  }

  async remove(conversationId: string, counterId: string, userId: string): Promise<void> {
    await this.assertDirectMember(conversationId, userId);
    const existing = await this.countersRepository.findById(counterId);
    if (!existing || existing.conversation_id !== conversationId) throw new NotFoundException('Counter not found');
    if (existing.author_id !== userId) throw new ForbiddenException('Only the author can delete a counter');
    await this.countersRepository.delete(counterId);
    await this.notifyChanged(conversationId, userId);
  }

  private async notifyChanged(conversationId: string, userId: string): Promise<void> {
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'counter.changed', conversationId });
  }

  private async assertDirectMember(conversationId: string, userId: string): Promise<void> {
    if (!(await this.conversationsRepository.isMember(conversationId, userId))) {
      throw new ForbiddenException('Not a member of this conversation');
    }
    if (await this.conversationsRepository.isGroup(conversationId)) {
      throw new BadRequestException('Counters are only available in direct conversations');
    }
  }
}
