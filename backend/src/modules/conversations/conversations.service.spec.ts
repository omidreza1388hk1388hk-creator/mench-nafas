import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { ConversationsService } from './conversations.service';
import { ConversationsRepository, ConversationRow, OtherMemberRow } from './conversations.repository';
import { UsersService } from '../users/users.service';
import { RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { NotificationsService } from '../notifications/notifications.service';

describe('ConversationsService', () => {
  let service: ConversationsService;
  let repo: jest.Mocked<ConversationsRepository>;
  let users: jest.Mocked<UsersService>;
  let broadcaster: jest.Mocked<RealtimeBroadcaster>;
  let notifications: jest.Mocked<NotificationsService>;

  const userA = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
  const userB = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

  const otherMemberB: OtherMemberRow = {
    id: userB,
    phone_e164: '+15559876543',
    display_name: 'Bee',
  };

  beforeEach(() => {
    repo = {
      findDirectBetween: jest.fn(),
      createDirect: jest.fn(),
      listForUser: jest.fn(),
      isMember: jest.fn(),
      listOtherMemberIds: jest.fn(),
      getOtherMember: jest.fn(),
      listOtherMembersForUser: jest.fn(),
      createGroup: jest.fn(),
      listMembers: jest.fn(),
      getRole: jest.fn(),
      addMembers: jest.fn(),
      removeMember: jest.fn(),
      updateTitle: jest.fn(),
      isGroup: jest.fn(),
      findById: jest.fn(),
      listMemberIds: jest.fn(),
    } as unknown as jest.Mocked<ConversationsRepository>;

    users = {
      findOrCreateByPhone: jest.fn(),
      findById: jest.fn(),
      findManyByIds: jest.fn(),
    } as unknown as jest.Mocked<UsersService>;

    broadcaster = {
      broadcastToUsers: jest.fn(),
      isUserConnected: jest.fn(),
    } as unknown as jest.Mocked<RealtimeBroadcaster>;

    notifications = {
      notifyNewMessage: jest.fn(),
      notifyAddedToGroup: jest.fn(),
    } as unknown as jest.Mocked<NotificationsService>;

    service = new ConversationsService(repo, users, broadcaster, notifications);
  });

  it('rejects starting a conversation with yourself', async () => {
    await expect(service.createDirect(userA, userA)).rejects.toBeInstanceOf(BadRequestException);
    expect(repo.createDirect).not.toHaveBeenCalled();
  });

  it('rejects when the target user does not exist', async () => {
    users.findById.mockResolvedValue(null);
    await expect(service.createDirect(userA, userB)).rejects.toBeInstanceOf(NotFoundException);
  });

  it('returns the existing conversation (with other-member info) instead of creating a duplicate', async () => {
    users.findById.mockResolvedValue({ id: userB } as any);
    const existing: ConversationRow = {
      id: 'conv-1',
      kind: 'direct',
      title: null,
      created_by: userA,
      created_at: new Date('2026-01-01T00:00:00.000Z'),
      last_message_at: null,
    };
    repo.findDirectBetween.mockResolvedValue(existing);
    repo.getOtherMember.mockResolvedValue(otherMemberB);

    const result = await service.createDirect(userA, userB);

    expect(result.id).toBe('conv-1');
    expect(result.otherUserId).toBe(userB);
    expect(result.otherUserDisplayName).toBe('Bee');
    expect(repo.createDirect).not.toHaveBeenCalled();
  });

  it('creates a new conversation when none exists yet, mapped to camelCase DTO fields including the other member', async () => {
    users.findById.mockResolvedValue({ id: userB } as any);
    repo.findDirectBetween.mockResolvedValue(null);
    const created: ConversationRow = {
      id: 'conv-2',
      kind: 'direct',
      title: null,
      created_by: userA,
      created_at: new Date('2026-01-01T00:00:00.000Z'),
      last_message_at: null,
    };
    repo.createDirect.mockResolvedValue(created);
    repo.getOtherMember.mockResolvedValue(otherMemberB);

    const result = await service.createDirect(userA, userB);

    expect(result).toEqual({
      id: 'conv-2',
      kind: 'direct',
      title: null,
      createdBy: userA,
      createdAt: '2026-01-01T00:00:00.000Z',
      lastMessageAt: null,
      otherUserId: userB,
      otherUserPhoneE164: '+15559876543',
      otherUserDisplayName: 'Bee',
    });
    expect(repo.createDirect).toHaveBeenCalledWith(userA, userB);
  });

  it('listForUser attaches the correct other member to each conversation via the batched lookup', async () => {
    const conv1: ConversationRow = {
      id: 'conv-1',
      kind: 'direct',
      title: null,
      created_by: userA,
      created_at: new Date('2026-01-01T00:00:00.000Z'),
      last_message_at: null,
    };
    repo.listForUser.mockResolvedValue([conv1]);
    repo.listOtherMembersForUser.mockResolvedValue(new Map([['conv-1', otherMemberB]]));

    const result = await service.listForUser(userA);

    expect(result).toHaveLength(1);
    expect(result[0].otherUserDisplayName).toBe('Bee');
  });

  it('assertMember throws NotFoundException (not Forbidden) for a non-member', async () => {
    repo.isMember.mockResolvedValue(false);
    await expect(service.assertMember('conv-1', userA)).rejects.toBeInstanceOf(NotFoundException);
  });

  it('createGroup rejects a blank title', async () => {
    await expect(service.createGroup(userA, '   ', [userB])).rejects.toBeInstanceOf(BadRequestException);
    expect(repo.createGroup).not.toHaveBeenCalled();
  });

  it('createGroup rejects when a proposed member does not exist', async () => {
    users.findManyByIds.mockResolvedValue([]); // requested [userB], found none
    await expect(service.createGroup(userA, 'Trip planning', [userB])).rejects.toBeInstanceOf(NotFoundException);
    expect(repo.createGroup).not.toHaveBeenCalled();
  });

  it('createGroup excludes the creator from memberIds even if the caller included it', async () => {
    users.findManyByIds.mockResolvedValue([{ id: userB } as any]);
    repo.createGroup.mockResolvedValue({
      id: 'conv-3',
      kind: 'group',
      title: 'Trip planning',
      created_by: userA,
      created_at: new Date('2026-01-01T00:00:00.000Z'),
      last_message_at: null,
    });

    await service.createGroup(userA, 'Trip planning', [userA, userB]);

    expect(repo.createGroup).toHaveBeenCalledWith(userA, 'Trip planning', [userB]);
  });

  it('removeMember allows leaving (removing yourself) without an owner check', async () => {
    repo.isMember.mockResolvedValue(true);
    repo.isGroup.mockResolvedValue(true);
    repo.listMemberIds.mockResolvedValue([userA, userB]);

    await service.removeMember('conv-3', userA, userA);

    expect(repo.getRole).not.toHaveBeenCalled();
    expect(repo.removeMember).toHaveBeenCalledWith('conv-3', userA);
  });

  it('removeMember rejects a non-owner removing someone else', async () => {
    repo.isMember.mockResolvedValue(true);
    repo.isGroup.mockResolvedValue(true);
    repo.getRole.mockResolvedValue('member');

    await expect(service.removeMember('conv-3', userA, userB)).rejects.toBeInstanceOf(ForbiddenException);
    expect(repo.removeMember).not.toHaveBeenCalled();
  });

  it('removeMember rejects on a direct (non-group) conversation', async () => {
    repo.isMember.mockResolvedValue(true);
    repo.isGroup.mockResolvedValue(false);

    await expect(service.removeMember('conv-1', userA, userA)).rejects.toBeInstanceOf(BadRequestException);
  });

  // --- Phase 6: realtime broadcasts on group mutations ---

  it('addMembers broadcasts group.member_added to the full post-add member list, and conversation.added to only the new member', async () => {
    repo.isMember.mockResolvedValue(true);
    repo.isGroup.mockResolvedValue(true);
    users.findManyByIds.mockResolvedValue([{ id: userB } as any]);
    repo.listMemberIds.mockResolvedValue([userA, userB]);
    const conversationRow: ConversationRow = {
      id: 'conv-3',
      kind: 'group',
      title: 'Trip planning',
      created_by: userA,
      created_at: new Date('2026-01-01T00:00:00.000Z'),
      last_message_at: null,
    };
    repo.findById.mockResolvedValue(conversationRow);
    users.findById.mockResolvedValue({ id: userA, display_name: 'Alpha' } as any);

    await service.addMembers('conv-3', userA, [userB]);

    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      [userA, userB],
      { type: 'group.member_added', conversationId: 'conv-3', userId: userB, addedBy: userA },
    );
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      [userB],
      expect.objectContaining({ type: 'conversation.added' }),
    );
    expect(notifications.notifyAddedToGroup).toHaveBeenCalledWith({
      recipientUserId: userB,
      conversationId: 'conv-3',
      conversationTitle: 'Trip planning',
      addedByDisplayName: 'Alpha',
    });
  });

  it('removeMember broadcasts to the member list from BEFORE the removal, so the removed user still gets it', async () => {
    repo.isMember.mockResolvedValue(true);
    repo.isGroup.mockResolvedValue(true);
    repo.getRole.mockResolvedValue('owner');
    repo.listMemberIds.mockResolvedValue([userA, userB]); // snapshot taken before removeMember() deletes the row

    await service.removeMember('conv-3', userA, userB);

    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      [userA, userB],
      { type: 'group.member_removed', conversationId: 'conv-3', userId: userB, removedBy: userA },
    );
  });

  it('removeMember (self) broadcasts group.member_left instead of group.member_removed', async () => {
    repo.isMember.mockResolvedValue(true);
    repo.isGroup.mockResolvedValue(true);
    repo.listMemberIds.mockResolvedValue([userA, userB]);

    await service.removeMember('conv-3', userB, userB);

    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      [userA, userB],
      { type: 'group.member_left', conversationId: 'conv-3', userId: userB },
    );
  });

  it('renameGroup broadcasts group.renamed to every current member after the write commits', async () => {
    repo.isMember.mockResolvedValue(true);
    repo.isGroup.mockResolvedValue(true);
    repo.listMemberIds.mockResolvedValue([userA, userB]);

    await service.renameGroup('conv-3', userA, '  New Title  ');

    expect(repo.updateTitle).toHaveBeenCalledWith('conv-3', 'New Title');
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      [userA, userB],
      { type: 'group.renamed', conversationId: 'conv-3', title: 'New Title', renamedBy: userA },
    );
  });
});
