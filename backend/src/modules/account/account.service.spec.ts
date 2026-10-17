import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { AccountService } from './account.service';
import { AccountRepository } from './account.repository';

describe('AccountService', () => {
  let repo: jest.Mocked<AccountRepository>;
  let service: AccountService;

  beforeEach(() => {
    repo = {
      getOrCreateInviteCode: jest.fn(),
      findByInviteCode: jest.fn(),
      findByUsername: jest.fn(),
      block: jest.fn(),
      isSenderBlockedInDirect: jest.fn(),
      deleteAccount: jest.fn(),
      allFlags: jest.fn(),
      activeAnnouncements: jest.fn(),
      countEvent: jest.fn(),
      addFeedback: jest.fn(),
    } as unknown as jest.Mocked<AccountRepository>;
    service = new AccountService(repo, { assertCanSend: jest.fn().mockResolvedValue(undefined) } as any);
  });

  it('refuses to block yourself', async () => {
    await expect(service.block('u1', 'u1')).rejects.toThrow(BadRequestException);
    expect(repo.block).not.toHaveBeenCalled();
  });

  it('rejects sending when the sender is blocked in a direct chat', async () => {
    repo.isSenderBlockedInDirect.mockResolvedValue(true);
    await expect(service.assertNotBlocked('c1', 'u1')).rejects.toThrow(ForbiddenException);
  });

  it('requires the literal word DELETE to delete an account', async () => {
    await expect(service.deleteAccount('u1', 'delete')).rejects.toThrow(BadRequestException);
    await service.deleteAccount('u1', 'DELETE');
    expect(repo.deleteAccount).toHaveBeenCalledWith('u1');
  });

  it('404s an unknown invite code', async () => {
    repo.findByInviteCode.mockResolvedValue(null);
    await expect(service.resolveInvite('NOPE1234')).rejects.toThrow(NotFoundException);
  });

  it('validates usernames before querying', async () => {
    await expect(service.byUsername('a b')).rejects.toThrow(BadRequestException);
    expect(repo.findByUsername).not.toHaveBeenCalled();
  });

  it('feature flag rollout is stable per user and respects 0 and 100', async () => {
    repo.activeAnnouncements.mockResolvedValue([]);
    repo.allFlags.mockResolvedValue([
      { key: 'on', enabled: true, rollout_pct: 100, description: null },
      { key: 'off', enabled: true, rollout_pct: 0, description: null },
      { key: 'disabled', enabled: false, rollout_pct: 100, description: null },
    ]);
    const a = await service.remoteConfig('user-1');
    const b = await service.remoteConfig('user-1');
    expect(a.flags).toEqual({ on: true, off: false, disabled: false });
    expect(b.flags).toEqual(a.flags);
  });

  it('only counts allow-listed analytics events', async () => {
    await expect(service.countEvent('read_all_messages')).rejects.toThrow(BadRequestException);
    await service.countEvent('app_open');
    expect(repo.countEvent).toHaveBeenCalledWith('app_open');
  });
});
