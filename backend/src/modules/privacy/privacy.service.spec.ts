import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { PrivacyService } from './privacy.service';
import { DEFAULT_PRIVACY, normalizeDisappearSeconds } from './privacy.types';

function build(settings = { ...DEFAULT_PRIVACY }, membership: any = { kind: 'direct', role: 'member' }) {
  const repo = {
    get: jest.fn().mockResolvedValue(settings),
    save: jest.fn().mockResolvedValue(undefined),
    membership: jest.fn().mockResolvedValue(membership),
    getDisappearSeconds: jest.fn().mockResolvedValue(null),
    setDisappearSeconds: jest.fn().mockResolvedValue(undefined),
    memberIds: jest.fn().mockResolvedValue(['u1', 'u2']),
  };
  const broadcaster = { broadcastToUsers: jest.fn(), isUserConnected: jest.fn() };
  return { service: new PrivacyService(repo as never, broadcaster as never), repo, broadcaster };
}

describe('normalizeDisappearSeconds', () => {
  it('treats null / 0 / undefined as off', () => {
    expect(normalizeDisappearSeconds(null)).toBeNull();
    expect(normalizeDisappearSeconds(0)).toBeNull();
    expect(normalizeDisappearSeconds(undefined)).toBeNull();
  });
  it('accepts the presets and rejects out-of-range or fractional values', () => {
    expect(normalizeDisappearSeconds(86_400)).toBe(86_400);
    expect(() => normalizeDisappearSeconds(59)).toThrow();
    expect(() => normalizeDisappearSeconds(31_536_001)).toThrow();
    expect(() => normalizeDisappearSeconds(120.5)).toThrow();
  });
});

describe('PrivacyService settings', () => {
  it('merges a partial patch into the stored settings', async () => {
    const { service, repo } = build();
    const result = await service.update('u1', { lastSeen: 'nobody' });
    expect(result).toEqual({ ...DEFAULT_PRIVACY, lastSeen: 'nobody' });
    expect(repo.save).toHaveBeenCalledWith('u1', result);
  });

  it('hidden users look like "not found" for discovery and photo', async () => {
    const { service } = build({ ...DEFAULT_PRIVACY, addMe: 'nobody', profilePhoto: 'nobody' });
    await expect(service.assertDiscoverable('target', 'me')).rejects.toBeInstanceOf(NotFoundException);
    await expect(service.assertPhotoVisible('target', 'me')).rejects.toBeInstanceOf(NotFoundException);
  });

  it('you can always see your own photo and find yourself', async () => {
    const { service } = build({ ...DEFAULT_PRIVACY, addMe: 'nobody', profilePhoto: 'nobody' });
    await expect(service.assertDiscoverable('me', 'me')).resolves.toBeUndefined();
    await expect(service.assertPhotoVisible('me', 'me')).resolves.toBeUndefined();
  });

  it('refuses calls to someone who disabled them', async () => {
    const { service } = build({ ...DEFAULT_PRIVACY, calls: 'nobody' });
    await expect(service.assertCallable('x')).rejects.toBeInstanceOf(ForbiddenException);
  });
});

describe('PrivacyService disappearing messages', () => {
  it('sets the timer and notifies every member', async () => {
    const { service, repo, broadcaster } = build();
    await expect(service.setDisappearing('c1', 'u1', 86_400)).resolves.toEqual({ seconds: 86_400 });
    expect(repo.setDisappearSeconds).toHaveBeenCalledWith('c1', 86_400);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      ['u1', 'u2'],
      expect.objectContaining({ type: 'conversation.disappearing_changed', seconds: 86_400, changedBy: 'u1' }),
    );
  });

  it('only a group owner may change a group timer', async () => {
    const { service } = build(undefined, { kind: 'group', role: 'member' });
    await expect(service.setDisappearing('c1', 'u1', 86_400)).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('a non-member gets 404, not 403', async () => {
    const { service } = build(undefined, null);
    await expect(service.getDisappearing('c1', 'u9')).rejects.toBeInstanceOf(NotFoundException);
  });
});
