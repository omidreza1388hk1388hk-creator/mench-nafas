import {
  BadRequestException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { MemoriesRepository } from '../memories/memories.repository';
import {
  REALTIME_BROADCASTER,
  RealtimeBroadcaster,
} from '../realtime/realtime-broadcaster.interface';
import { AlbumRepository, AlbumRow } from './album.repository';

export const MAX_ALBUM_ITEMS = 300;

export interface AlbumItemDto {
  id: string;
  attachmentId: string;
  caption: string;
  isMine: boolean;
  myReaction: string | null;
  otherReaction: string | null;
  createdAt: string;
}

function toDto(row: AlbumRow, viewerId: string): AlbumItemDto {
  return {
    id: row.id,
    attachmentId: row.attachment_id,
    caption: row.caption,
    isMine: row.author_id === viewerId,
    myReaction: row.my_reaction,
    otherReaction: row.other_reaction,
    createdAt: new Date(row.created_at).toISOString(),
  };
}

@Injectable()
export class AlbumService {
  constructor(
    private readonly albumRepository: AlbumRepository,
    private readonly memoriesRepository: MemoriesRepository,
    private readonly conversationsRepository: ConversationsRepository,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
  ) {}

  async list(conversationId: string, userId: string): Promise<AlbumItemDto[]> {
    await this.assertDirectMember(conversationId, userId);
    return (await this.albumRepository.list(conversationId, userId)).map((r) => toDto(r, userId));
  }

  async create(
    conversationId: string,
    userId: string,
    dto: { attachmentId: string; caption?: string },
  ): Promise<AlbumItemDto> {
    await this.assertDirectMember(conversationId, userId);
    if (
      !(await this.memoriesRepository.isImageAttachmentOfConversation(
        dto.attachmentId,
        conversationId,
      ))
    ) {
      throw new BadRequestException('attachmentId must be an image uploaded to this conversation');
    }
    if ((await this.albumRepository.count(conversationId)) >= MAX_ALBUM_ITEMS) {
      throw new BadRequestException(`The album holds at most ${MAX_ALBUM_ITEMS} photos`);
    }
    const row = await this.albumRepository.create(
      conversationId,
      userId,
      dto.attachmentId,
      dto.caption?.trim() ?? '',
    );
    await this.notifyChanged(conversationId, userId);
    return toDto(row, userId);
  }

  /** Sets (or, with null, removes) your reaction to a photo. */
  async react(
    conversationId: string,
    itemId: string,
    userId: string,
    emoji: string | null,
  ): Promise<void> {
    await this.assertDirectMember(conversationId, userId);
    await this.findInConversation(conversationId, itemId);
    await this.albumRepository.setReaction(itemId, userId, emoji);
    await this.notifyChanged(conversationId, userId);
  }

  async remove(conversationId: string, itemId: string, userId: string): Promise<void> {
    await this.assertDirectMember(conversationId, userId);
    const item = await this.findInConversation(conversationId, itemId);
    if (item.author_id !== userId)
      throw new ForbiddenException('Only the author can delete a photo');
    await this.albumRepository.delete(itemId);
    await this.notifyChanged(conversationId, userId);
  }

  private async findInConversation(conversationId: string, itemId: string) {
    const item = await this.albumRepository.findById(itemId);
    if (!item || item.conversation_id !== conversationId)
      throw new NotFoundException('Photo not found');
    return item;
  }

  private async notifyChanged(conversationId: string, userId: string): Promise<void> {
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'album.changed', conversationId });
  }

  private async assertDirectMember(conversationId: string, userId: string): Promise<void> {
    if (!(await this.conversationsRepository.isMember(conversationId, userId))) {
      throw new ForbiddenException('Not a member of this conversation');
    }
    if (await this.conversationsRepository.isGroup(conversationId)) {
      throw new BadRequestException('The album is only available in direct conversations');
    }
  }
}
