import { sanitizeStrokes } from './strokes';

const ok = { color: '#FF8800', width: 6, points: [0.1, 0.2, 0.3, 0.4] };

describe('sanitizeStrokes', () => {
  it('accepts a valid stroke, lower-cases the color and drops unknown fields', () => {
    expect(sanitizeStrokes([{ ...ok, evil: 'x' }])).toEqual([{ color: '#ff8800', width: 6, points: [0.1, 0.2, 0.3, 0.4] }]);
  });

  it('rejects non-arrays, empty arrays and oversized requests', () => {
    expect(() => sanitizeStrokes('x')).toThrow();
    expect(() => sanitizeStrokes([])).toThrow();
    expect(() => sanitizeStrokes(Array(41).fill(ok))).toThrow();
  });

  it('rejects bad colors, widths and points', () => {
    expect(() => sanitizeStrokes([{ ...ok, color: 'red' }])).toThrow();
    expect(() => sanitizeStrokes([{ ...ok, width: 0 }])).toThrow();
    expect(() => sanitizeStrokes([{ ...ok, width: 99 }])).toThrow();
    expect(() => sanitizeStrokes([{ ...ok, points: [0.1] }])).toThrow();
    expect(() => sanitizeStrokes([{ ...ok, points: [0.1, 0.2, 0.3] }])).toThrow();
    expect(() => sanitizeStrokes([{ ...ok, points: [0.1, 1.5] }])).toThrow();
    expect(() => sanitizeStrokes([{ ...ok, points: [0.1, NaN] }])).toThrow();
    expect(() => sanitizeStrokes([{ ...ok, points: Array(1202).fill(0.5) }])).toThrow();
  });
});
