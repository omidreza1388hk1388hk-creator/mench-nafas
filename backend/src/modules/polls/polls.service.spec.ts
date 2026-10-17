import { BadRequestException, ConflictException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { PollDto } from './poll.dto';
import { cleanOptions, PollsService } from './polls.service';

const CONV = 'conv-1';
const ME = 'user-me';
const OTHER = 'user-other';

function poll(overrides: Partial<PollDto> = {}): PollDto {
  return {
    id: 'poll-1',
    conversationId: CONV,
    creatorId: ME,
    question: 'Dinner?',
    multiple: false,
    closed: false,
    createdAt: '2026-01-01T00:00:00.000Z',
    options: [
      { id: 'o1', text: 'Pizza', voterIds: [] },
      { id: 'o2', text: 'Sushi', voterIds: [] },
    ],
    ...overrides,
  };
}

function build(initial: PollDto | null = poll()) {
  const repo = {
    create: jest.fn().mockResolvedValue('poll-1'),
    findDto: jest.fn().mockResolvedValue(initial),
    listOptionIds: jest.fn().mockResolvedValue(['o1', 'o2']),
    replaceVotes: jest.fn().mockResolvedValue(undefined),
    close: jest.fn().mockResolvedValue(undefined),
  };
  const conversations = { assertMember: jest.fn().mockResolvedValue(undefined) };
  const conversationsRepo = { listOtherMemberIds: jest.fn().mockResolvedValue([OTHER]) };
  const broadcaster = { broadcastToUsers: jest.fn() };
  const service = new PollsService(repo as any, conversations as any, conversationsRepo as any, broadcaster as any);
  return { service, repo, conversations, broadcaster };
}

describe('cleanOptions', () => {
  it('trims, collapses spaces, drops blanks and case-insensitive duplicates', () => {
    expect(cleanOptions(['  Pizza ', 'pizza', '', '  ', 'Sushi   rolls'])).toEqual(['Pizza', 'Sushi rolls']);
  });
});

describe('PollsService', () => {
  it('creates a poll for a member with cleaned options', async () => {
    const { service, repo, conversations } = build();
    await service.create({ conversationId: CONV, creatorId: ME, question: '  Dinner?  ', options: ['Pizza', 'pizza', 'Sushi'], multiple: true });
    expect(conversations.assertMember).toHaveBeenCalledWith(CONV, ME);
    expect(repo.create).toHaveBeenCalledWith({ conversationId: CONV, creatorId: ME, question: 'Dinner?', multiple: true, options: ['Pizza', 'Sushi'] });
  });

  it('rejects a poll that has fewer than two distinct options or a blank question', async () => {
    const { service } = build();
    await expect(service.create({ conversationId: CONV, creatorId: ME, question: 'Q', options: ['A', 'a'] })).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.create({ conversationId: CONV, creatorId: ME, question: '   ', options: ['A', 'B'] })).rejects.toBeInstanceOf(BadRequestException);
  });

  it('a vote replaces the voter\'s previous choice and is broadcast to the others', async () => {
    const { service, repo, broadcaster } = build();
    await service.vote('poll-1', OTHER, ['o2']);
    expect(repo.replaceVotes).toHaveBeenCalledWith('poll-1', OTHER, ['o2']);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith([OTHER], expect.objectContaining({ type: 'poll.updated', conversationId: CONV }));
  });

  it('an empty vote retracts', async () => {
    const { service, repo } = build();
    await service.vote('poll-1', OTHER, []);
    expect(repo.replaceVotes).toHaveBeenCalledWith('poll-1', OTHER, []);
  });

  it('single-choice polls reject several answers; multiple-choice polls accept them and de-duplicate', async () => {
    const single = build();
    await expect(single.service.vote('poll-1', OTHER, ['o1', 'o2'])).rejects.toBeInstanceOf(BadRequestException);
    const multi = build(poll({ multiple: true }));
    await multi.service.vote('poll-1', OTHER, ['o1', 'o2', 'o1']);
    expect(multi.repo.replaceVotes).toHaveBeenCalledWith('poll-1', OTHER, ['o1', 'o2']);
  });

  it('rejects options that do not belong to the poll and votes on a closed poll', async () => {
    const { service } = build();
    await expect(service.vote('poll-1', OTHER, ['not-an-option'])).rejects.toBeInstanceOf(BadRequestException);
    const closed = build(poll({ closed: true }));
    await expect(closed.service.vote('poll-1', OTHER, ['o1'])).rejects.toBeInstanceOf(ConflictException);
  });

  it('only a member can read or vote', async () => {
    const { service, conversations } = build();
    conversations.assertMember.mockRejectedValue(new NotFoundException());
    await expect(service.get('poll-1', 'stranger')).rejects.toBeInstanceOf(NotFoundException);
    await expect(service.vote('poll-1', 'stranger', ['o1'])).rejects.toBeInstanceOf(NotFoundException);
  });

  it('only the creator can close, and closing is broadcast', async () => {
    const { service, repo, broadcaster } = build();
    await expect(service.close('poll-1', OTHER)).rejects.toBeInstanceOf(ForbiddenException);
    await service.close('poll-1', ME);
    expect(repo.close).toHaveBeenCalledWith('poll-1');
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledTimes(1);
  });

  it('answers 404 for an unknown poll', async () => {
    const { service } = build(null);
    await expect(service.get('missing', ME)).rejects.toBeInstanceOf(NotFoundException);
  });
});
