import { BadRequestException, NotFoundException } from '@nestjs/common';
import { MAX_PENDING_PER_CONVERSATION, parseSendAt, ScheduledMessagesService } from './scheduled-messages.service';

const NOW = Date.parse('2026-10-05T12:00:00.000Z');
const row = (over: Record<string, unknown> = {}) => ({
  id: 'sm-1', conversation_id: 'conv-1', sender_id: 'me', client_msg_id: 'cm-1', body: 'happy anniversary', silent: false,
  send_at: new Date(NOW + 60_000), status: 'pending', created_at: new Date(NOW), ...over,
});

function build() {
  const repo = {
    countPending: jest.fn().mockResolvedValue(0),
    create: jest.fn().mockImplementation(async (i) => row({ body: i.body, silent: i.silent, send_at: i.sendAt })),
    listPending: jest.fn().mockResolvedValue([row()]),
    deletePending: jest.fn().mockResolvedValue(true),
    claimDue: jest.fn().mockResolvedValue([]),
    markSent: jest.fn().mockResolvedValue(undefined),
    markFailed: jest.fn().mockResolvedValue(undefined),
    releaseStuck: jest.fn().mockResolvedValue(undefined),
  };
  const conversations = { assertMember: jest.fn().mockResolvedValue(undefined) };
  const messages = { send: jest.fn().mockResolvedValue({}) };
  const service = new ScheduledMessagesService(repo as any, conversations as any, messages as any, () => NOW);
  return { service, repo, conversations, messages };
}

describe('parseSendAt', () => {
  it('accepts a time in the allowed window and rejects past, too-soon, too-far and garbage', () => {
    expect(parseSendAt(new Date(NOW + 60_000).toISOString(), NOW).getTime()).toBe(NOW + 60_000);
    expect(() => parseSendAt(new Date(NOW - 1000).toISOString(), NOW)).toThrow(BadRequestException);
    expect(() => parseSendAt(new Date(NOW + 2000).toISOString(), NOW)).toThrow(BadRequestException);
    expect(() => parseSendAt(new Date(NOW + 400 * 24 * 3600 * 1000).toISOString(), NOW)).toThrow(BadRequestException);
    expect(() => parseSendAt('not a date', NOW)).toThrow(BadRequestException);
  });
});

describe('ScheduledMessagesService', () => {
  it('schedules a trimmed message for a member', async () => {
    const { service, repo, conversations } = build();
    const dto = await service.schedule({ conversationId: 'conv-1', senderId: 'me', clientMsgId: 'cm-1', body: '  hi  ', sendAt: new Date(NOW + 60_000).toISOString(), silent: true });
    expect(conversations.assertMember).toHaveBeenCalledWith('conv-1', 'me');
    expect(repo.create).toHaveBeenCalledWith(expect.objectContaining({ body: 'hi', silent: true }));
    expect(dto.status).toBe('pending');
  });

  it('rejects empty text, a bad time and too many pending messages', async () => {
    const { service, repo } = build();
    const base = { conversationId: 'conv-1', senderId: 'me', clientMsgId: 'cm', sendAt: new Date(NOW + 60_000).toISOString() };
    await expect(service.schedule({ ...base, body: '   ' })).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.schedule({ ...base, body: 'x', sendAt: new Date(NOW).toISOString() })).rejects.toBeInstanceOf(BadRequestException);
    repo.countPending.mockResolvedValue(MAX_PENDING_PER_CONVERSATION);
    await expect(service.schedule({ ...base, body: 'x' })).rejects.toBeInstanceOf(BadRequestException);
  });

  it('cancel removes only a pending message of the caller', async () => {
    const { service, repo } = build();
    await service.cancel('sm-1', 'me');
    expect(repo.deletePending).toHaveBeenCalledWith('sm-1', 'me');
    repo.deletePending.mockResolvedValue(false);
    await expect(service.cancel('sm-1', 'me')).rejects.toBeInstanceOf(NotFoundException);
  });

  it('the worker sends due messages through MessagesService with the same client id and silent flag, then marks them sent', async () => {
    const { service, repo, messages } = build();
    repo.claimDue.mockResolvedValue([row({ silent: true })]);
    expect(await service.tick()).toBe(1);
    expect(messages.send).toHaveBeenCalledWith({ conversationId: 'conv-1', senderId: 'me', clientMsgId: 'cm-1', body: 'happy anniversary', silent: true });
    expect(repo.markSent).toHaveBeenCalledWith('sm-1');
    expect(repo.releaseStuck).toHaveBeenCalled();
  });

  it('a failing send is marked failed and does not stop the rest of the batch', async () => {
    const { service, repo, messages } = build();
    repo.claimDue.mockResolvedValue([row({ id: 'a' }), row({ id: 'b', client_msg_id: 'cm-2' })]);
    messages.send.mockRejectedValueOnce(new Error('not a member')).mockResolvedValueOnce({});
    expect(await service.tick()).toBe(1);
    expect(repo.markFailed).toHaveBeenCalledWith('a', 'not a member');
    expect(repo.markSent).toHaveBeenCalledWith('b');
  });

  it('survives a repository failure and never overlaps ticks', async () => {
    const { service, repo } = build();
    repo.claimDue.mockRejectedValueOnce(new Error('db down'));
    await expect(service.tick()).resolves.toBe(0);
    let release!: () => void;
    repo.claimDue.mockImplementationOnce(() => new Promise((resolve) => { release = () => resolve([]); }));
    const first = service.tick();
    await expect(service.tick()).resolves.toBe(0); // overlapped tick is a no-op
    await Promise.resolve();
    release();
    await first;
  });
});
