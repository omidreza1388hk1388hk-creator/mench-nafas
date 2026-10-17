/** Phase 3A — daily-ritual streak, derived from the days on which BOTH people answered the question of the day. */

const DAY_MS = 86_400_000;

function dayNumber(isoDate: string): number {
  return Math.floor(Date.parse(`${isoDate}T00:00:00Z`) / DAY_MS);
}

/**
 * `bothAnsweredDates`: YYYY-MM-DD days where both answered (any order, duplicates ok).
 * `today`: the current YYYY-MM-DD in the same calendar.
 *
 * current — length of the run of consecutive days ending today, or ending yesterday when today
 *           is not complete yet (so the streak stays alive until the day is over).
 * best    — the longest run ever.
 */
export function computeStreak(
  bothAnsweredDates: string[],
  today: string,
): { current: number; best: number } {
  const days = [...new Set(bothAnsweredDates.map(dayNumber).filter((n) => !Number.isNaN(n)))].sort(
    (a, b) => a - b,
  );
  if (days.length === 0) return { current: 0, best: 0 };

  let best = 1;
  let run = 1;
  for (let i = 1; i < days.length; i++) {
    run = days[i] === days[i - 1] + 1 ? run + 1 : 1;
    if (run > best) best = run;
  }

  const todayN = dayNumber(today);
  const last = days[days.length - 1];
  if (last !== todayN && last !== todayN - 1) return { current: 0, best };
  let current = 1;
  for (let i = days.length - 1; i > 0 && days[i] === days[i - 1] + 1; i--) current++;
  return { current, best };
}
