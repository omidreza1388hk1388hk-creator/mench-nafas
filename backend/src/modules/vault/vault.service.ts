import {
  BadRequestException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import {
  REALTIME_BROADCASTER,
  RealtimeBroadcaster,
} from '../realtime/realtime-broadcaster.interface';
import { VaultRepository, VaultRow } from './vault.repository';

export const MAX_VAULT_ITEMS = 100;

export interface VaultItemDto {
  id: string;
  conversationId: string;
  blob: string;
  isMine: boolean;
  createdAt: string;
}

function toDto(row: VaultRow, viewerId: string): VaultItemDto {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    blob: row.blob,
    isMine: row.author_id === viewerId,
    createdAt: new Date(row.created_at).toISOString(),
  };
}

@Injectable()
export class VaultService {
  constructor(
    private readonly vaultRepository: VaultRepository,
    private readonly conversationsRepository: ConversationsRepository,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
  ) {}

  async list(conversationId: string, userId: string): Promise<VaultItemDto[]> {
    await this.assertDirectMember(conversationId, userId);
    return (await this.vaultRepository.list(conversationId)).map((r) => toDto(r, userId));
  }

  async create(conversationId: string, userId: string, blob: string): Promise<VaultItemDto> {
    await this.assertDirectMember(conversationId, userId);
    if ((await this.vaultRepository.count(conversationId)) >= MAX_VAULT_ITEMS) {
      throw new BadRequestException(`The vault holds at most ${MAX_VAULT_ITEMS} notes`);
    }
    const row = await this.vaultRepository.create(conversationId, userId, blob);
    await this.notifyChanged(conversationId, userId);
    return toDto(row, userId);
  }

  async remove(conversationId: string, itemId: string, userId: string): Promise<void> {
    await this.assertDirectMember(conversationId, userId);
    const item = await this.vaultRepository.findById(itemId);
    if (!item || item.conversation_id !== conversationId)
      throw new NotFoundException('Note not found');
    if (item.author_id !== userId)
      throw new ForbiddenException('Only the author can delete a note');
    await this.vaultRepository.delete(itemId);
    await this.notifyChanged(conversationId, userId);
  }

  private async notifyChanged(conversationId: string, userId: string): Promise<void> {
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'vault.changed', conversationId });
  }

  private async assertDirectMember(conversationId: string, userId: string): Promise<void> {
    if (!(await this.conversationsRepository.isMember(conversationId, userId))) {
      throw new ForbiddenException('Not a member of this conversation');
    }
    if (await this.conversationsRepository.isGroup(conversationId)) {
      throw new BadRequestException('The vault is only available in direct conversations');
    }
  }
}
