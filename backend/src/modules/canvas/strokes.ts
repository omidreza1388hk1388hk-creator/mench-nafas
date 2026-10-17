/** Phase 3A — validation of strokes sent to the shared canvas. Coordinates are normalised 0..1 so any screen size renders the same picture. */

export interface Stroke {
  color: string;
  width: number;
  points: number[];
  by?: string;
}

export const MAX_STROKES_PER_CANVAS = 400;
export const MAX_STROKES_PER_REQUEST = 40;
export const MAX_POINT_VALUES_PER_STROKE = 1200;

const COLOR = /^#[0-9a-fA-F]{6}$/;

/** Returns clean strokes (only known fields) or throws an Error with a short reason. */
export function sanitizeStrokes(input: unknown): Stroke[] {
  if (!Array.isArray(input)) throw new Error('strokes must be an array');
  if (input.length === 0) throw new Error('strokes must not be empty');
  if (input.length > MAX_STROKES_PER_REQUEST) throw new Error('too many strokes in one request');
  return input.map((raw, i) => {
    if (typeof raw !== 'object' || raw === null) throw new Error(`stroke ${i} is not an object`);
    const { color, width, points } = raw as Record<string, unknown>;
    if (typeof color !== 'string' || !COLOR.test(color)) throw new Error(`stroke ${i}: bad color`);
    if (typeof width !== 'number' || !Number.isFinite(width) || width < 1 || width > 40) throw new Error(`stroke ${i}: bad width`);
    if (!Array.isArray(points) || points.length < 2 || points.length % 2 !== 0 || points.length > MAX_POINT_VALUES_PER_STROKE) {
      throw new Error(`stroke ${i}: bad points`);
    }
    for (const p of points) {
      if (typeof p !== 'number' || !Number.isFinite(p) || p < 0 || p > 1) throw new Error(`stroke ${i}: points must be numbers in 0..1`);
    }
    return { color: color.toLowerCase(), width, points: points as number[] };
  });
}
