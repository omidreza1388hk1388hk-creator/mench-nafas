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
import { MemoriesRepository } from '../memories/memories.repository';
import { StoriesRepository, StoryRow } from './stories.repository';

export const MAX_ACTIVE_STORIES_PER_AUTHOR = 10;

export interface StoryDto {
  id: string;
  conversationId: string;
  body: string;
  emoji: string;
  palette: number;
  attachmentId: string | null;
  createdAt: string;
  expiresAt: string;
  isMine: boolean;
  /** For someone else's story: whether the viewer has already seen it. For your own: always true. */
  seen: boolean;
  /** For your own story: how many other people have seen it. */
  views: number;
}

function toDto(row: StoryRow, viewerId: string): StoryDto {
  const mine = row.author_id === viewerId;
  return {
    id: row.id,
    conversationId: row.conversation_id,
    body: row.body,
    emoji: row.emoji,
    palette: row.palette,
    attachmentId: row.attachment_id ?? null,
    createdAt: new Date(row.created_at).toISOString(),
    expiresAt: new Date(row.expires_at).toISOString(),
    isMine: mine,
    seen: mine ? true : row.seen_by_viewer,
    views: mine ? Number(row.views) : 0,
  };
}

@Injectable()
export class StoriesService {
  constructor(
    private readonly storiesRepository: StoriesRepository,
    private readonly conversationsRepository: ConversationsRepository,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
    private readonly memoriesRepository: MemoriesRepository,
  ) {}

  async list(conversationId: string, userId: string): Promise<StoryDto[]> {
    await this.assertMember(conversationId, userId);
    return (await this.storiesRepository.listActive(conversationId, userId)).map((r) =>
      toDto(r, userId),
    );
  }

  /** For the chat list: conversations that have stories you have not seen, with the count. */
  async unseen(userId: string): Promise<{ conversationId: string; count: number }[]> {
    return (await this.storiesRepository.unseenByConversation(userId)).map((r) => ({
      conversationId: r.conversation_id,
      count: Number(r.n),
    }));
  }

  async create(
    conversationId: string,
    userId: string,
    dto: { body: string; emoji?: string; palette: number; attachmentId?: string },
  ): Promise<StoryDto> {
    await this.assertMember(conversationId, userId);
    if (
      dto.attachmentId &&
      !(await this.memoriesRepository.isImageAttachmentOfConversation(
        dto.attachmentId,
        conversationId,
      ))
    ) {
      throw new BadRequestException('attachmentId must be an image uploaded to this conversation');
    }
    const body = dto.body.trim();
    if (!body) throw new BadRequestException('A story needs some text');
    if (
      (await this.storiesRepository.countActiveByAuthor(conversationId, userId)) >=
      MAX_ACTIVE_STORIES_PER_AUTHOR
    ) {
      throw new BadRequestException(`At most ${MAX_ACTIVE_STORIES_PER_AUTHOR} active stories`);
    }
    const row = await this.storiesRepository.create(
      conversationId,
      userId,
      body,
      dto.emoji?.trim() ?? '',
      dto.palette,
      dto.attachmentId ?? null,
    );
    await this.notifyChanged(conversationId, userId);
    return toDto(row, userId);
  }

  /** Marks a story seen. Authors viewing their own story, and expired stories, are ignored quietly. */
  async markViewed(conversationId: string, storyId: string, userId: string): Promise<void> {
    await this.assertMember(conversationId, userId);
    const story = await this.storiesRepository.findById(storyId);
    if (!story || story.conversation_id !== conversationId)
      throw new NotFoundException('Story not found');
    if (story.author_id === userId || new Date(story.expires_at).getTime() <= Date.now()) return;
    await this.storiesRepository.markViewed(storyId, userId);
    this.realtimeBroadcaster.broadcastToUsers([story.author_id], {
      type: 'story.changed',
      conversationId,
    });
  }

  async remove(conversationId: string, storyId: string, userId: string): Promise<void> {
    await this.assertMember(conversationId, userId);
    const story = await this.storiesRepository.findById(storyId);
    if (!story || story.conversation_id !== conversationId)
      throw new NotFoundException('Story not found');
    if (story.author_id !== userId)
      throw new ForbiddenException('Only the author can delete a story');
    await this.storiesRepository.delete(storyId);
    await this.notifyChanged(conversationId, userId);
  }

  private async notifyChanged(conversationId: string, userId: string): Promise<void> {
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'story.changed', conversationId });
  }

  private async assertMember(conversationId: string, userId: string): Promise<void> {
    if (!(await this.conversationsRepository.isMember(conversationId, userId))) {
      throw new ForbiddenException('Not a member of this conversation');
    }
  }
}
