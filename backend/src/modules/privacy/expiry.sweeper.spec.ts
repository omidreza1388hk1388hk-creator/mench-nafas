import { ExpirySweeper } from './expiry.sweeper';

describe('ExpirySweeper', () => {
  it('broadcasts message.deleted to every member of each affected conversation', async () => {
    const at = new Date('2026-10-04T10:00:00Z');
    const repo = {
      sweepExpired: jest
        .fn()
        .mockResolvedValueOnce([
          { id: 'm1', conversation_id: 'c1', deleted_at: at },
          { id: 'm2', conversation_id: 'c2', deleted_at: at },
        ]),
      memberIds: jest.fn().mockImplementation(async (c: string) => (c === 'c1' ? ['a', 'b'] : ['c', 'd'])),
    };
    const broadcaster = { broadcastToUsers: jest.fn(), isUserConnected: jest.fn() };
    const sweeper = new ExpirySweeper(repo as never, broadcaster as never);

    await expect(sweeper.sweepOnce()).resolves.toBe(2);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(['a', 'b'], {
      type: 'message.deleted',
      conversationId: 'c1',
      messageId: 'm1',
      deletedAt: at.toISOString(),
    });
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledTimes(2);
  });

  it('does nothing when nothing is due and survives a database error', async () => {
    const repo = { sweepExpired: jest.fn().mockResolvedValue([]), memberIds: jest.fn() };
    const sweeper = new ExpirySweeper(repo as never, { broadcastToUsers: jest.fn() } as never);
    await expect(sweeper.sweepOnce()).resolves.toBe(0);

    repo.sweepExpired.mockRejectedValueOnce(new Error('db down'));
    await expect(sweeper.sweepOnce()).resolves.toBe(0);
  });
});
