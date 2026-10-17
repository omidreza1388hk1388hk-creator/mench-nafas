import { computeStreak } from './streak';

describe('computeStreak', () => {
  it('is zero with no days', () => {
    expect(computeStreak([], '2026-10-04')).toEqual({ current: 0, best: 0 });
  });

  it('counts a run ending today', () => {
    expect(computeStreak(['2026-10-02', '2026-10-03', '2026-10-04'], '2026-10-04')).toEqual({ current: 3, best: 3 });
  });

  it('keeps the streak alive through yesterday when today is not complete yet', () => {
    expect(computeStreak(['2026-10-02', '2026-10-03'], '2026-10-04')).toEqual({ current: 2, best: 2 });
  });

  it('resets when a whole day was missed, but remembers the best run', () => {
    expect(computeStreak(['2026-09-01', '2026-09-02', '2026-09-03', '2026-10-03'], '2026-10-04')).toEqual({ current: 1, best: 3 });
    expect(computeStreak(['2026-09-01', '2026-09-02', '2026-09-03'], '2026-10-04')).toEqual({ current: 0, best: 3 });
  });

  it('crosses month and year boundaries and ignores duplicates and junk', () => {
    expect(computeStreak(['2025-12-31', '2026-01-01', '2026-01-01', 'junk'], '2026-01-01')).toEqual({ current: 2, best: 2 });
  });
});
