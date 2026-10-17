import { isLocked, lockUntilAfterFailure, passwordProblem, MAX_FAILED_ATTEMPTS, LOCK_MINUTES } from './two-step.policy';

describe('two-step policy', () => {
  it('rejects short, long and repeated-character passwords', () => {
    expect(passwordProblem('12345')).toMatch(/at least/);
    expect(passwordProblem('x'.repeat(65))).toMatch(/at most/);
    expect(passwordProblem('aaaaaaaa')).toMatch(/repeated/);
    expect(passwordProblem('correct horse')).toBeNull();
  });

  it('locks only from the 5th consecutive failure, for 15 minutes', () => {
    const now = new Date('2026-10-04T10:00:00Z');
    expect(lockUntilAfterFailure(MAX_FAILED_ATTEMPTS - 1, now)).toBeNull();
    const until = lockUntilAfterFailure(MAX_FAILED_ATTEMPTS, now)!;
    expect(until.getTime() - now.getTime()).toBe(LOCK_MINUTES * 60_000);
  });

  it('isLocked is false for null and past dates, true for future', () => {
    const now = new Date('2026-10-04T10:00:00Z');
    expect(isLocked(null, now)).toBe(false);
    expect(isLocked(new Date('2026-10-04T09:59:59Z'), now)).toBe(false);
    expect(isLocked(new Date('2026-10-04T10:00:01Z'), now)).toBe(true);
  });
});
