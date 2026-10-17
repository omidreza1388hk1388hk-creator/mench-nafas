import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { MAX_VAULT_ITEMS, VaultService } from './vault.service';

const userA = 'user-a';
const userB = 'user-b';
const blob = 'v1:QUJDREVGR0hJSktMTU5PUA==';

function row(over: Record<string, unknown> = {}) {
  return { id: 'v-1', conversation_id: 'conv-1', author_id: userA, blob, created_at: new Date('2026-01-01T00:00:00Z'), ...over };
}

describe('VaultService', () => {
  let vault: Record<string, jest.Mock>;
  let conversations: Record<string, jest.Mock>;
  let broadcaster: Record<string, jest.Mock>;
  let service: VaultService;

  beforeEach(() => {
    vault = { list: jest.fn().mockResolvedValue([]), count: jest.fn().mockResolvedValue(0), create: jest.fn(), findById: jest.fn(), delete: jest.fn() };
    conversations = {
      isMember: jest.fn().mockResolvedValue(true),
      isGroup: jest.fn().mockResolvedValue(false),
      listOtherMemberIds: jest.fn().mockResolvedValue([userB]),
    };
    broadcaster = { broadcastToUsers: jest.fn() };
    service = new VaultService(vault as any, conversations as any, broadcaster as any);
  });

  it('returns blobs untouched and marks which are mine', async () => {
    vault.list.mockResolvedValue([row(), row({ id: 'v-2', author_id: userB })]);
    const [a, b] = await service.list('conv-1', userA);
    expect(a).toMatchObject({ blob, isMine: true });
    expect(b).toMatchObject({ blob, isMine: false });
  });

  it('stores the opaque blob as-is and notifies the partner', async () => {
    vault.create.mockResolvedValue(row());
    await service.create('conv-1', userA, blob);
    expect(vault.create).toHaveBeenCalledWith('conv-1', userA, blob);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith([userB], { type: 'vault.changed', conversationId: 'conv-1' });
  });

  it('enforces the cap', async () => {
    vault.count.mockResolvedValue(MAX_VAULT_ITEMS);
    await expect(service.create('conv-1', userA, blob)).rejects.toBeInstanceOf(BadRequestException);
  });

  it('only the author can delete; wrong-conversation notes are not found', async () => {
    vault.findById.mockResolvedValue(row({ author_id: userB }));
    await expect(service.remove('conv-1', 'v-1', userA)).rejects.toBeInstanceOf(ForbiddenException);
    vault.findById.mockResolvedValue(row({ conversation_id: 'other' }));
    await expect(service.remove('conv-1', 'v-1', userA)).rejects.toBeInstanceOf(NotFoundException);
    vault.findById.mockResolvedValue(row());
    await service.remove('conv-1', 'v-1', userA);
    expect(vault.delete).toHaveBeenCalledWith('v-1');
  });

  it('is unavailable in groups and to non-members', async () => {
    conversations.isGroup.mockResolvedValue(true);
    await expect(service.list('conv-1', userA)).rejects.toBeInstanceOf(BadRequestException);
    conversations.isMember.mockResolvedValue(false);
    await expect(service.list('conv-1', userA)).rejects.toBeInstanceOf(ForbiddenException);
  });
});
