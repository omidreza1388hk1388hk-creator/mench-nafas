import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { AlbumService, MAX_ALBUM_ITEMS } from './album.service';

const userA = 'user-a';
const userB = 'user-b';
const row = (over: Record<string, unknown> = {}) => ({
  id: 'i-1', conversation_id: 'conv-1', author_id: userA, attachment_id: 'att-1', caption: 'us', created_at: new Date(), my_reaction: null, other_reaction: '😍', ...over,
});

describe('AlbumService', () => {
  let album: Record<string, jest.Mock>;
  let memories: Record<string, jest.Mock>;
  let conversations: Record<string, jest.Mock>;
  let broadcaster: Record<string, jest.Mock>;
  let service: AlbumService;

  beforeEach(() => {
    album = { list: jest.fn().mockResolvedValue([row()]), count: jest.fn().mockResolvedValue(0), create: jest.fn().mockResolvedValue(row()), findById: jest.fn().mockResolvedValue(row()), setReaction: jest.fn(), delete: jest.fn() };
    memories = { isImageAttachmentOfConversation: jest.fn().mockResolvedValue(true) };
    conversations = { isMember: jest.fn().mockResolvedValue(true), isGroup: jest.fn().mockResolvedValue(false), listOtherMemberIds: jest.fn().mockResolvedValue([userB]) };
    broadcaster = { broadcastToUsers: jest.fn() };
    service = new AlbumService(album as any, memories as any, conversations as any, broadcaster as any);
  });

  it('lists photos with both reactions', async () => {
    expect((await service.list('conv-1', userA))[0]).toMatchObject({ isMine: true, otherReaction: '😍', myReaction: null });
  });

  it('adds only an image that belongs to this conversation, trims the caption and notifies', async () => {
    await service.create('conv-1', userA, { attachmentId: 'att-1', caption: ' us ' });
    expect(album.create).toHaveBeenCalledWith('conv-1', userA, 'att-1', 'us');
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith([userB], { type: 'album.changed', conversationId: 'conv-1' });
    memories.isImageAttachmentOfConversation.mockResolvedValue(false);
    await expect(service.create('conv-1', userA, { attachmentId: 'x' })).rejects.toBeInstanceOf(BadRequestException);
  });

  it('enforces the album size cap', async () => {
    album.count.mockResolvedValue(MAX_ALBUM_ITEMS);
    await expect(service.create('conv-1', userA, { attachmentId: 'att-1' })).rejects.toBeInstanceOf(BadRequestException);
  });

  it('either person can react (or clear a reaction); photos of other conversations are not found', async () => {
    await service.react('conv-1', 'i-1', userB, '💗');
    expect(album.setReaction).toHaveBeenCalledWith('i-1', userB, '💗');
    await service.react('conv-1', 'i-1', userB, null);
    expect(album.setReaction).toHaveBeenLastCalledWith('i-1', userB, null);
    album.findById.mockResolvedValue(row({ conversation_id: 'other' }));
    await expect(service.react('conv-1', 'i-1', userB, '💗')).rejects.toBeInstanceOf(NotFoundException);
  });

  it('only the author can delete', async () => {
    await expect(service.remove('conv-1', 'i-1', userB)).rejects.toBeInstanceOf(ForbiddenException);
    await service.remove('conv-1', 'i-1', userA);
    expect(album.delete).toHaveBeenCalledWith('i-1');
  });

  it('is unavailable in groups and to non-members', async () => {
    conversations.isGroup.mockResolvedValue(true);
    await expect(service.list('conv-1', userA)).rejects.toBeInstanceOf(BadRequestException);
    conversations.isMember.mockResolvedValue(false);
    await expect(service.list('conv-1', userA)).rejects.toBeInstanceOf(ForbiddenException);
  });
});
