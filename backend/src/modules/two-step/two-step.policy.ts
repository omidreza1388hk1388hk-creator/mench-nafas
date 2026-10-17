/** Pure rules for two-step verification, kept free of I/O so they are trivial to unit-test. */
export const MIN_PASSWORD_LENGTH = 6;
export const MAX_PASSWORD_LENGTH = 64;
export const MAX_FAILED_ATTEMPTS = 5;
export const LOCK_MINUTES = 15;
export const TICKET_TTL_MINUTES = 5;
export const MAX_TICKET_ATTEMPTS = 5;

export function passwordProblem(password: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) return `Password must be at least ${MIN_PASSWORD_LENGTH} characters`;
  if (password.length > MAX_PASSWORD_LENGTH) return `Password must be at most ${MAX_PASSWORD_LENGTH} characters`;
  if (/^(.)\1+$/.test(password)) return 'Password cannot be a single repeated character';
  return null;
}

/** After this many consecutive failures the account is locked for LOCK_MINUTES. */
export function lockUntilAfterFailure(failedAttemptsIncludingThis: number, now: Date): Date | null {
  if (failedAttemptsIncludingThis < MAX_FAILED_ATTEMPTS) return null;
  return new Date(now.getTime() + LOCK_MINUTES * 60_000);
}

export function isLocked(lockedUntil: Date | null, now: Date): boolean {
  return lockedUntil !== null && lockedUntil.getTime() > now.getTime();
}
