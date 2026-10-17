import {
  allowed, canChangeRole, canRemoveMember, decideSend, inviteProblem, isAdmin, parseSettingsPatch, toRole,
} from './group-policy';

describe('group policy', () => {
  it('maps roles and admin-ness', () => {
    expect(toRole('owner')).toBe('owner');
    expect(toRole('boss')).toBeNull();
    expect(toRole(undefined)).toBeNull();
    expect(isAdmin('owner')).toBe(true);
    expect(isAdmin('admin')).toBe(true);
    expect(isAdmin('member')).toBe(false);
    expect(isAdmin(null)).toBe(false);
  });

  it('a permission open to "all" lets every member in, "admins" only admins and owner, and non-members never', () => {
    expect(allowed('all', 'member')).toBe(true);
    expect(allowed('admins', 'member')).toBe(false);
    expect(allowed('admins', 'admin')).toBe(true);
    expect(allowed('admins', 'owner')).toBe(true);
    expect(allowed('all', null)).toBe(false);
  });

  it('removal rules: owner > admin > member, nobody removes the owner', () => {
    expect(canRemoveMember('owner', 'admin')).toBe(true);
    expect(canRemoveMember('owner', 'member')).toBe(true);
    expect(canRemoveMember('owner', 'owner')).toBe(false);
    expect(canRemoveMember('admin', 'member')).toBe(true);
    expect(canRemoveMember('admin', 'admin')).toBe(false);
    expect(canRemoveMember('admin', 'owner')).toBe(false);
    expect(canRemoveMember('member', 'member')).toBe(false);
    expect(canRemoveMember(null, 'member')).toBe(false);
    expect(canRemoveMember('owner', null)).toBe(false);
  });

  it('only the owner changes roles, and only between admin and member', () => {
    expect(canChangeRole('owner', 'member', 'admin')).toBe(true);
    expect(canChangeRole('owner', 'admin', 'member')).toBe(true);
    expect(canChangeRole('owner', 'owner', 'admin')).toBe(false);
    expect(canChangeRole('owner', 'member', 'owner')).toBe(false);
    expect(canChangeRole('owner', 'member', 'root')).toBe(false);
    expect(canChangeRole('admin', 'member', 'admin')).toBe(false);
    expect(canChangeRole('member', 'member', 'admin')).toBe(false);
  });

  it('channels (send = admins) block plain members', () => {
    const channel = { sendMessages: 'admins' as const, slowModeSeconds: 0 };
    expect(decideSend(channel, 'member', null, 1000)).toEqual({ ok: false, reason: 'admins_only' });
    expect(decideSend(channel, 'admin', null, 1000)).toEqual({ ok: true });
    expect(decideSend(channel, 'owner', null, 1000)).toEqual({ ok: true });
  });

  it('slow mode makes a member wait, reports the remaining seconds, and never applies to admins', () => {
    const slow = { sendMessages: 'all' as const, slowModeSeconds: 30 };
    const last = 100_000;
    expect(decideSend(slow, 'member', last, last + 10_000)).toEqual({ ok: false, reason: 'slow_mode', retryAfterSeconds: 20 });
    expect(decideSend(slow, 'member', last, last + 29_001)).toEqual({ ok: false, reason: 'slow_mode', retryAfterSeconds: 1 });
    expect(decideSend(slow, 'member', last, last + 30_000)).toEqual({ ok: true });
    expect(decideSend(slow, 'member', null, last)).toEqual({ ok: true });
    expect(decideSend(slow, 'admin', last, last + 1000)).toEqual({ ok: true });
    expect(decideSend({ sendMessages: 'all', slowModeSeconds: 0 }, 'member', last, last + 1)).toEqual({ ok: true });
  });

  it('validates settings patches strictly', () => {
    expect(parseSettingsPatch({ sendMessages: 'admins', slowModeSeconds: 60 })).toEqual({ sendMessages: 'admins', slowModeSeconds: 60 });
    expect(parseSettingsPatch({ pinMessages: 'admins' })).toEqual({ pinMessages: 'admins' });
    expect(parseSettingsPatch({})).toBeNull();
    expect(parseSettingsPatch({ sendMessages: 'nobody' })).toBeNull();
    expect(parseSettingsPatch({ slowModeSeconds: 45 })).toBeNull();
    expect(parseSettingsPatch({ slowModeSeconds: '60' })).toBeNull();
    expect(parseSettingsPatch({ unknownField: 1 })).toBeNull();
  });

  it('explains why an invite is unusable', () => {
    const base = { expiresAtMs: null, maxUses: null, uses: 0, revoked: false };
    expect(inviteProblem(base, 1000)).toBeNull();
    expect(inviteProblem({ ...base, revoked: true }, 1000)).toBe('revoked');
    expect(inviteProblem({ ...base, expiresAtMs: 1000 }, 1000)).toBe('expired');
    expect(inviteProblem({ ...base, expiresAtMs: 2000 }, 1000)).toBeNull();
    expect(inviteProblem({ ...base, maxUses: 2, uses: 2 }, 1000)).toBe('used_up');
    expect(inviteProblem({ ...base, maxUses: 2, uses: 1 }, 1000)).toBeNull();
  });
});
