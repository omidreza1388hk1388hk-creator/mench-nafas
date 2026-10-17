import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { MAX_ACTIVE_STORIES_PER_AUTHOR, StoriesService } from './stories.service';

const userA = 'user-a';
const userB = 'user-b';
const future = new Date(Date.now() + 3_600_000);

function row(over: Record<string, unknown> = {}) {
  return {
    id: 's-1', conversation_id: 'conv-1', author_id: userA, body: 'hello', emoji: '💗', palette: 2,
    created_at: new Date(), expires_at: future, seen_by_viewer: false, views: 3, ...over,
  };
}

describe('StoriesService', () => {
  let stories: Record<string, jest.Mock>;
  let conversations: Record<string, jest.Mock>;
  let broadcaster: Record<string, jest.Mock>;
  let service: StoriesService;

  beforeEach(() => {
    stories = {
      listActive: jest.fn().mockResolvedValue([]), countActiveByAuthor: jest.fn().mockResolvedValue(0),
      create: jest.fn(), findById: jest.fn(), markViewed: jest.fn(), delete: jest.fn(),
    };
    conversations = { isMember: jest.fn().mockResolvedValue(true), listOtherMemberIds: jest.fn().mockResolvedValue([userB]) };
    broadcaster = { broadcastToUsers: jest.fn() };
    service = new StoriesService(stories as any, conversations as any, broadcaster as any, { isImageAttachmentOfConversation: jest.fn().mockResolvedValue(true) } as any);
  });

  it('lists stories with viewer-specific flags (own stories are always seen and show their view count)', async () => {
    stories.listActive.mockResolvedValue([row(), row({ id: 's-2', author_id: userB, views: 9 })]);
    const [mine, theirs] = await service.list('conv-1', userA);
    expect(mine).toMatchObject({ isMine: true, seen: true, views: 3 });
    expect(theirs).toMatchObject({ isMine: false, seen: false, views: 0 });
  });

  it('reports conversations with unseen stories for the chat-list rings', async () => {
    stories.unseenByConversation = jest.fn().mockResolvedValue([{ conversation_id: 'conv-1', n: 2 }]);
    await expect(service.unseen(userA)).resolves.toEqual([{ conversationId: 'conv-1', count: 2 }]);
  });

  it('creates a trimmed story and tells the others', async () => {
    stories.create.mockResolvedValue(row());
    await service.create('conv-1', userA, { body: '  hello  ', emoji: ' 💗 ', palette: 2 });
    expect(stories.create).toHaveBeenCalledWith('conv-1', userA, 'hello', '💗', 2, null);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith([userB], { type: 'story.changed', conversationId: 'conv-1' });
  });

  it('rejects blank text and enforces the active-story cap', async () => {
    await expect(service.create('conv-1', userA, { body: '   ', palette: 0 })).rejects.toBeInstanceOf(BadRequestException);
    stories.countActiveByAuthor.mockResolvedValue(MAX_ACTIVE_STORIES_PER_AUTHOR);
    await expect(service.create('conv-1', userA, { body: 'x', palette: 0 })).rejects.toBeInstanceOf(BadRequestException);
  });

  it('records a view from the partner and notifies the author, but ignores own and expired stories', async () => {
    stories.findById.mockResolvedValue(row());
    await service.markViewed('conv-1', 's-1', userB);
    expect(stories.markViewed).toHaveBeenCalledWith('s-1', userB);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith([userA], { type: 'story.changed', conversationId: 'conv-1' });

    stories.markViewed.mockClear();
    await service.markViewed('conv-1', 's-1', userA);
    stories.findById.mockResolvedValue(row({ expires_at: new Date(Date.now() - 1000) }));
    await service.markViewed('conv-1', 's-1', userB);
    expect(stories.markViewed).not.toHaveBeenCalled();
  });

  it('only the author can delete; other-conversation stories are not found; non-members are rejected', async () => {
    stories.findById.mockResolvedValue(row({ author_id: userB }));
    await expect(service.remove('conv-1', 's-1', userA)).rejects.toBeInstanceOf(ForbiddenException);
    stories.findById.mockResolvedValue(row({ conversation_id: 'other' }));
    await expect(service.remove('conv-1', 's-1', userA)).rejects.toBeInstanceOf(NotFoundException);
    stories.findById.mockResolvedValue(row());
    await service.remove('conv-1', 's-1', userA);
    expect(stories.delete).toHaveBeenCalledWith('s-1');

    conversations.isMember.mockResolvedValue(false);
    await expect(service.list('conv-1', userA)).rejects.toBeInstanceOf(ForbiddenException);
  });
});
