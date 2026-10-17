import { sanitizeAnswers, sanitizeQuestions, scoreAnswers } from './quiz';

const good = [{ q: ' Favourite colour? ', options: [' Red ', 'Blue'], correct: 1 }];

describe('quiz helpers', () => {
  it('trims and accepts valid questions', () => {
    expect(sanitizeQuestions(good)).toEqual([{ q: 'Favourite colour?', options: ['Red', 'Blue'], correct: 1 }]);
  });

  it('rejects bad shapes', () => {
    expect(() => sanitizeQuestions([])).toThrow();
    expect(() => sanitizeQuestions(Array(9).fill(good[0]))).toThrow();
    expect(() => sanitizeQuestions([{ ...good[0], options: ['only one'] }])).toThrow();
    expect(() => sanitizeQuestions([{ ...good[0], options: ['a', 'b', 'c', 'd', 'e'] }])).toThrow();
    expect(() => sanitizeQuestions([{ ...good[0], correct: 2 }])).toThrow();
    expect(() => sanitizeQuestions([{ ...good[0], correct: -1 }])).toThrow();
    expect(() => sanitizeQuestions([{ ...good[0], q: '' }])).toThrow();
    expect(() => sanitizeQuestions('x')).toThrow();
  });

  it('validates and scores answers', () => {
    const qs = sanitizeQuestions([good[0], { q: 'Pet?', options: ['Cat', 'Dog', 'Fish'], correct: 2 }]);
    expect(sanitizeAnswers(qs, [1, 2])).toEqual([1, 2]);
    expect(() => sanitizeAnswers(qs, [1])).toThrow();
    expect(() => sanitizeAnswers(qs, [1, 3])).toThrow();
    expect(scoreAnswers(qs, [1, 2])).toBe(2);
    expect(scoreAnswers(qs, [0, 2])).toBe(1);
    expect(scoreAnswers(qs, [0, 0])).toBe(0);
  });
});
