import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import {
  generateInviteCode,
  GroupAdminService,
  MAX_ACTIVE_INVITES,
  normalizeInviteCode,
} from './group-admin.service';

const NOW = Date.parse('2026-10-05T12:00:00.000Z');
const CONV = 'conv-1';

function invite(over: Record<string, unknown> = {}) {
  return {
    id: 'inv-1',
    conversation_id: CONV,
    code: 'ABCDEFGH2345',
    created_by: 'owner-1',
    expires_at: null,
    max_uses: null,
    uses: 0,
    revoked_at: null,
    created_at: new Date(NOW),
    ...over,
  };
}

function build(role: string | null = 'owner') {
  const repo = {
    createInvite: jest
      .fn()
      .mockImplementation(async (i) =>
        invite({ code: i.code, expires_at: i.expiresAt, max_uses: i.maxUses }),
      ),
    listInvites: jest.fn().mockResolvedValue([invite()]),
    countActiveInvites: jest.fn().mockResolvedValue(0),
    revokeInvite: jest.fn().mockResolvedValue(true),
    findByCode: jest.fn().mockResolvedValue(invite()),
    consumeUse: jest.fn().mockResolvedValue(true),
    memberCount: jest.fn().mockResolvedValue(3),
  };
  const roles: Record<string, string | null> = { me: role, target: 'member', owner: 'owner' };
  const convRepo = {
    isGroup: jest.fn().mockResolvedValue(true),
    getRole: jest.fn().mockImplementation(async (_c: string, u: string) => roles[u] ?? null),
    getGroupSettings: jest.fn().mockResolvedValue({
      sendMessages: 'all',
      addMembers: 'all',
      editInfo: 'all',
      pinMessages: 'all',
      slowModeSeconds: 0,
    }),
    updateGroupSettings: jest.fn().mockResolvedValue(undefined),
    setRole: jest.fn().mockResolvedValue(undefined),
    findById: jest.fn().mockResolvedValue({
      id: CONV,
      kind: 'group',
      title: 'Us',
      created_by: 'owner-1',
      created_at: new Date(NOW),
      updated_at: new Date(NOW),
    }),
    addMembers: jest.fn().mockResolvedValue(undefined),
    listMemberIds: jest.fn().mockResolvedValue(['owner-1', 'joiner']),
  };
  const convService = { assertMember: jest.fn().mockResolvedValue(undefined) };
  const broadcaster = { broadcastToUsers: jest.fn() };
  const service = new GroupAdminService(
    repo as any,
    convRepo as any,
    convService as any,
    broadcaster as any,
    () => NOW,
  );
  return { service, repo, convRepo, convService, broadcaster };
}

describe('invite codes', () => {
  it('are 12 readable characters, normalised on input', () => {
    const code = generateInviteCode();
    expect(code).toMatch(/^[A-HJ-NP-Z2-9]{12}$/);
    expect(generateInviteCode()).not.toBe(code);
    expect(normalizeInviteCode(' abcd-efgh 2345 ')).toBe('ABCDEFGH2345');
  });
});

describe('GroupAdminService', () => {
  it("shows settings and the caller's role, flagging admins-only groups as channels", async () => {
    const { service, convRepo } = build('member');
    expect(await service.getInfo(CONV, 'me')).toMatchObject({
      myRole: 'member',
      isChannel: false,
      slowModeSeconds: 0,
    });
    convRepo.getGroupSettings.mockResolvedValue({
      sendMessages: 'admins',
      addMembers: 'admins',
      editInfo: 'admins',
      pinMessages: 'admins',
      slowModeSeconds: 30,
    });
    expect(await service.getInfo(CONV, 'me')).toMatchObject({
      isChannel: true,
      slowModeSeconds: 30,
    });
  });

  it('rejects direct chats', async () => {
    const { service, convRepo } = build();
    convRepo.isGroup.mockResolvedValue(false);
    await expect(service.getInfo(CONV, 'me')).rejects.toBeInstanceOf(BadRequestException);
  });

  it('only admins change settings, and only with valid values', async () => {
    const member = build('member');
    await expect(
      member.service.updateSettings(CONV, 'me', { slowModeSeconds: 60 }),
    ).rejects.toBeInstanceOf(ForbiddenException);
    const admin = build('admin');
    await admin.service.updateSettings(CONV, 'me', { sendMessages: 'admins', slowModeSeconds: 60 });
    expect(admin.convRepo.updateGroupSettings).toHaveBeenCalledWith(CONV, {
      sendMessages: 'admins',
      slowModeSeconds: 60,
    });
    await expect(
      admin.service.updateSettings(CONV, 'me', { slowModeSeconds: 45 }),
    ).rejects.toBeInstanceOf(BadRequestException);
    await expect(admin.service.updateSettings(CONV, 'me', {})).rejects.toBeInstanceOf(
      BadRequestException,
    );
  });

  it('only the owner changes roles', async () => {
    const owner = build('owner');
    await owner.service.changeRole(CONV, 'me', 'target', 'admin');
    expect(owner.convRepo.setRole).toHaveBeenCalledWith(CONV, 'target', 'admin');
    await expect(owner.service.changeRole(CONV, 'me', 'owner', 'member')).rejects.toBeInstanceOf(
      ForbiddenException,
    );
    await expect(owner.service.changeRole(CONV, 'me', 'stranger', 'admin')).rejects.toBeInstanceOf(
      NotFoundException,
    );
    const admin = build('admin');
    await expect(admin.service.changeRole(CONV, 'me', 'target', 'admin')).rejects.toBeInstanceOf(
      ForbiddenException,
    );
  });

  it('admins create invites with an expiry and a use limit; members cannot; the number of live links is capped', async () => {
    const { service, repo } = build('admin');
    const dto = await service.createInvite(CONV, 'me', { expiresInHours: 24, maxUses: 5 });
    expect(repo.createInvite).toHaveBeenCalledWith(
      expect.objectContaining({ maxUses: 5, expiresAt: new Date(NOW + 24 * 3600_000) }),
    );
    expect(dto.code).toMatch(/^[A-HJ-NP-Z2-9]{12}$/);
    repo.countActiveInvites.mockResolvedValue(MAX_ACTIVE_INVITES);
    await expect(service.createInvite(CONV, 'me', {})).rejects.toBeInstanceOf(BadRequestException);
    await expect(build('member').service.createInvite(CONV, 'me', {})).rejects.toBeInstanceOf(
      ForbiddenException,
    );
  });

  it('previews a code without joining it', async () => {
    const { service, repo } = build('member');
    expect(await service.preview('abcd-efgh-2345')).toEqual({
      valid: true,
      problem: null,
      title: 'Us',
      memberCount: 3,
    });
    repo.findByCode.mockResolvedValue(invite({ expires_at: new Date(NOW - 1) }));
    expect(await service.preview('x')).toMatchObject({ valid: false, problem: 'expired' });
    repo.findByCode.mockResolvedValue(null);
    expect(await service.preview('nope')).toEqual({
      valid: false,
      problem: null,
      title: null,
      memberCount: 0,
    });
  });

  it('joining adds the member as a plain member, uses one invite use and tells the group', async () => {
    const { service, repo, convRepo, broadcaster } = build('member');
    await service.join('ABCDEFGH2345', 'joiner');
    expect(repo.consumeUse).toHaveBeenCalledWith('inv-1');
    expect(convRepo.addMembers).toHaveBeenCalledWith(CONV, ['joiner']);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      ['owner-1', 'joiner'],
      expect.objectContaining({ type: 'group.member_added', userId: 'joiner' }),
    );
  });

  it('joining twice is idempotent and costs no invite use', async () => {
    const { service, repo, convRepo } = build('member');
    convRepo.getRole.mockResolvedValue('member'); // already a member
    await service.join('ABCDEFGH2345', 'joiner');
    expect(repo.consumeUse).not.toHaveBeenCalled();
    expect(convRepo.addMembers).not.toHaveBeenCalled();
  });

  it('refuses revoked, expired and used-up invites, and unknown codes', async () => {
    const { service, repo, convRepo } = build('member');
    convRepo.getRole.mockResolvedValue(null);
    repo.findByCode.mockResolvedValue(invite({ revoked_at: new Date(NOW) }));
    await expect(service.join('x', 'joiner')).rejects.toBeInstanceOf(ConflictException);
    repo.findByCode.mockResolvedValue(invite({ max_uses: 1, uses: 1 }));
    await expect(service.join('x', 'joiner')).rejects.toBeInstanceOf(ConflictException);
    repo.findByCode.mockResolvedValue(null);
    await expect(service.join('x', 'joiner')).rejects.toBeInstanceOf(NotFoundException);
    repo.findByCode.mockResolvedValue(invite());
    repo.consumeUse.mockResolvedValue(false); // lost a race for the last use
    await expect(service.join('x', 'joiner')).rejects.toBeInstanceOf(ConflictException);
    expect(convRepo.addMembers).not.toHaveBeenCalled();
  });

  it('revoking a missing invite is a 404', async () => {
    const { service, repo } = build('admin');
    repo.revokeInvite.mockResolvedValue(false);
    await expect(service.revokeInvite(CONV, 'me', 'inv-x')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });
});

describe('GroupAdminService.transferOwnership', () => {
  it('lets the owner hand the group to a member', async () => {
    const { service, convRepo } = build('owner');
    (convRepo as any).transferOwnership = jest.fn().mockResolvedValue(undefined);
    await service.transferOwnership(CONV, 'me', 'target');
    expect((convRepo as any).transferOwnership).toHaveBeenCalledWith(CONV, 'me', 'target');
  });

  it('refuses non-owners, self-transfer and non-members', async () => {
    const admin = build('admin');
    (admin.convRepo as any).transferOwnership = jest.fn();
    await expect(admin.service.transferOwnership(CONV, 'me', 'target')).rejects.toBeInstanceOf(
      ForbiddenException,
    );

    const owner = build('owner');
    (owner.convRepo as any).transferOwnership = jest.fn();
    await expect(owner.service.transferOwnership(CONV, 'me', 'me')).rejects.toBeInstanceOf(
      BadRequestException,
    );
    await expect(owner.service.transferOwnership(CONV, 'me', 'stranger')).rejects.toBeInstanceOf(
      NotFoundException,
    );
    expect((owner.convRepo as any).transferOwnership).not.toHaveBeenCalled();
  });
});
