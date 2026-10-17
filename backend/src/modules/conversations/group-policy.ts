/**
 * Phase 2A — who may do what in a group. Pure functions (no I/O) so every rule is unit-tested.
 * Roles: owner (exactly one, the creator) > admin > member. A "channel" is a group whose
 * sendMessages permission is 'admins'.
 */
export type GroupRole = 'owner' | 'admin' | 'member';
export type Who = 'all' | 'admins';

export interface GroupSettings {
  sendMessages: Who;
  addMembers: Who;
  editInfo: Who;
  pinMessages: Who;
  /** Seconds a plain member must wait between their own messages; 0 = off. */
  slowModeSeconds: number;
}

export const SLOW_MODE_OPTIONS = [0, 10, 30, 60, 300, 900, 3600] as const;

export const DEFAULT_GROUP_SETTINGS: GroupSettings = {
  sendMessages: 'all',
  addMembers: 'all',
  editInfo: 'all',
  pinMessages: 'all',
  slowModeSeconds: 0,
};

export function toRole(raw: string | null | undefined): GroupRole | null {
  return raw === 'owner' || raw === 'admin' || raw === 'member' ? raw : null;
}

export function isAdmin(role: GroupRole | null): boolean {
  return role === 'owner' || role === 'admin';
}

/** True when [role] satisfies a permission that is open to [who]. */
export function allowed(who: Who, role: GroupRole | null): boolean {
  if (role === null) return false;
  return who === 'all' || isAdmin(role);
}

/** The owner can remove anyone else; an admin can remove plain members only; members cannot remove others. */
export function canRemoveMember(actor: GroupRole | null, target: GroupRole | null): boolean {
  if (actor === null || target === null) return false;
  if (actor === 'owner') return target !== 'owner';
  if (actor === 'admin') return target === 'member';
  return false;
}

/** Only the owner changes roles; the owner's own role is fixed; the new role must be admin or member. */
export function canChangeRole(
  actor: GroupRole | null,
  target: GroupRole | null,
  next: string,
): next is 'admin' | 'member' {
  return (
    actor === 'owner' &&
    target !== null &&
    target !== 'owner' &&
    (next === 'admin' || next === 'member')
  );
}

export type SendDecision =
  | { ok: true }
  | { ok: false; reason: 'admins_only' }
  | { ok: false; reason: 'slow_mode'; retryAfterSeconds: number };

/** Can [role] send now? Admins/owner are exempt from slow mode. [lastSentAtMs] is this member's previous message time. */
export function decideSend(
  settings: Pick<GroupSettings, 'sendMessages' | 'slowModeSeconds'>,
  role: GroupRole | null,
  lastSentAtMs: number | null,
  nowMs: number,
): SendDecision {
  if (!allowed(settings.sendMessages, role)) return { ok: false, reason: 'admins_only' };
  if (settings.slowModeSeconds > 0 && !isAdmin(role) && lastSentAtMs !== null) {
    const waitMs = lastSentAtMs + settings.slowModeSeconds * 1000 - nowMs;
    if (waitMs > 0)
      return { ok: false, reason: 'slow_mode', retryAfterSeconds: Math.ceil(waitMs / 1000) };
  }
  return { ok: true };
}

/** Validates a partial settings update from a client; returns only the fields that are valid, or null if any is invalid. */
export function parseSettingsPatch(input: Record<string, unknown>): Partial<GroupSettings> | null {
  const patch: Partial<GroupSettings> = {};
  for (const key of ['sendMessages', 'addMembers', 'editInfo', 'pinMessages'] as const) {
    if (input[key] === undefined) continue;
    if (input[key] !== 'all' && input[key] !== 'admins') return null;
    patch[key] = input[key] as Who;
  }
  if (input.slowModeSeconds !== undefined) {
    const seconds = input.slowModeSeconds;
    if (typeof seconds !== 'number' || !(SLOW_MODE_OPTIONS as readonly number[]).includes(seconds))
      return null;
    patch.slowModeSeconds = seconds;
  }
  return Object.keys(patch).length > 0 ? patch : null;
}

/** Why an invite code cannot be used (or null if it can). [nowMs] is injected for testing. */
export function inviteProblem(
  invite: { expiresAtMs: number | null; maxUses: number | null; uses: number; revoked: boolean },
  nowMs: number,
): 'revoked' | 'expired' | 'used_up' | null {
  if (invite.revoked) return 'revoked';
  if (invite.expiresAtMs !== null && invite.expiresAtMs <= nowMs) return 'expired';
  if (invite.maxUses !== null && invite.uses >= invite.maxUses) return 'used_up';
  return null;
}
