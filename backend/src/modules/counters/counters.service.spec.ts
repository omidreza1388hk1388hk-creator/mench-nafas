import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { CountersService, MAX_COUNTERS_PER_CONVERSATION } from './counters.service';

const userA = 'user-a';
const userB = 'user-b';

function row(over: Record<string, unknown> = {}) {
  return {
    id: 'c-1',
    conversation_id: 'conv-1',
    author_id: userA,
    title: 'Days together',
    emoji: '💗',
    kind: 'date',
    anchor_date: '2024-02-14',
    tally_count: 0,
    created_at: new Date('2026-01-01T00:00:00Z'),
    ...over,
  };
}

describe('CountersService', () => {
  let counters: Record<string, jest.Mock>;
  let conversations: Record<string, jest.Mock>;
  let broadcaster: Record<string, jest.Mock>;
  let service: CountersService;

  beforeEach(() => {
    counters = {
      list: jest.fn(), count: jest.fn().mockResolvedValue(0), create: jest.fn(),
      findById: jest.fn(), bump: jest.fn(), delete: jest.fn(),
    };
    conversations = {
      isMember: jest.fn().mockResolvedValue(true),
      isGroup: jest.fn().mockResolvedValue(false),
      listOtherMemberIds: jest.fn().mockResolvedValue([userB]),
    };
    broadcaster = { broadcastToUsers: jest.fn(), isUserConnected: jest.fn() };
    service = new CountersService(counters as any, conversations as any, broadcaster as any);
  });

  it('creates a date counter and tells the partner', async () => {
    counters.create.mockResolvedValue(row());
    const dto = await service.create('conv-1', userA, { title: ' Days together ', kind: 'date', anchorDate: '2024-02-14' });
    expect(counters.create).toHaveBeenCalledWith('conv-1', userA, { title: 'Days together', emoji: '💗', kind: 'date', anchorDate: '2024-02-14', theme: 0 });
    expect(dto).toMatchObject({ id: 'c-1', kind: 'date', isMine: true });
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith([userB], { type: 'counter.changed', conversationId: 'conv-1' });
  });

  it('stores the chosen card theme', async () => {
    counters.create.mockResolvedValue(row({ theme: 3 }));
    const dto = await service.create('conv-1', userA, { title: 'Trip', kind: 'date', anchorDate: '2027-01-01', theme: 3 });
    expect(counters.create.mock.calls[0][2].theme).toBe(3);
    expect(dto.theme).toBe(3);
  });

  it('rejects a date counter without a valid date', async () => {
    await expect(service.create('conv-1', userA, { title: 'x', kind: 'date' })).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.create('conv-1', userA, { title: 'x', kind: 'date', anchorDate: 'nope' })).rejects.toBeInstanceOf(BadRequestException);
    expect(counters.create).not.toHaveBeenCalled();
  });

  it('ignores anchorDate for a tally counter', async () => {
    counters.create.mockResolvedValue(row({ kind: 'tally', anchor_date: null }));
    await service.create('conv-1', userA, { title: 'Hugs', kind: 'tally', anchorDate: '2024-02-14' });
    expect(counters.create.mock.calls[0][2].anchorDate).toBeNull();
  });

  it('enforces the per-conversation cap', async () => {
    counters.count.mockResolvedValue(MAX_COUNTERS_PER_CONVERSATION);
    await expect(service.create('conv-1', userA, { title: 'x', kind: 'tally' })).rejects.toBeInstanceOf(BadRequestException);
  });

  it('lets either person bump a tally, but not a date counter', async () => {
    counters.findById.mockResolvedValue(row({ kind: 'tally', author_id: userB }));
    counters.bump.mockResolvedValue(row({ kind: 'tally', author_id: userB, tally_count: 5 }));
    const dto = await service.bump('conv-1', 'c-1', userA, 1);
    expect(dto.tallyCount).toBe(5);

    counters.findById.mockResolvedValue(row({ kind: 'date' }));
    await expect(service.bump('conv-1', 'c-1', userA, 1)).rejects.toBeInstanceOf(BadRequestException);
  });

  it('only the author can delete, and a counter from another conversation is not found', async () => {
    counters.findById.mockResolvedValue(row({ author_id: userB }));
    await expect(service.remove('conv-1', 'c-1', userA)).rejects.toBeInstanceOf(ForbiddenException);

    counters.findById.mockResolvedValue(row({ conversation_id: 'other' }));
    await expect(service.remove('conv-1', 'c-1', userA)).rejects.toBeInstanceOf(NotFoundException);
    expect(counters.delete).not.toHaveBeenCalled();

    counters.findById.mockResolvedValue(row());
    await service.remove('conv-1', 'c-1', userA);
    expect(counters.delete).toHaveBeenCalledWith('c-1');
  });

  it('is unavailable in groups and to non-members', async () => {
    conversations.isGroup.mockResolvedValue(true);
    await expect(service.list('conv-1', userA)).rejects.toBeInstanceOf(BadRequestException);
    conversations.isMember.mockResolvedValue(false);
    await expect(service.list('conv-1', userA)).rejects.toBeInstanceOf(ForbiddenException);
  });
});
