/** Phase 3A — validation and scoring for the couple quiz. Pure functions, no I/O. */

export interface QuizQuestion {
  q: string;
  options: string[];
  correct: number;
}

export const MAX_QUESTIONS = 8;

/** Returns clean questions or throws an Error with a short reason. */
export function sanitizeQuestions(input: unknown): QuizQuestion[] {
  if (!Array.isArray(input) || input.length < 1 || input.length > MAX_QUESTIONS) {
    throw new Error(`a quiz needs 1 to ${MAX_QUESTIONS} questions`);
  }
  return input.map((raw, i) => {
    if (typeof raw !== 'object' || raw === null) throw new Error(`question ${i + 1} is not an object`);
    const { q, options, correct } = raw as Record<string, unknown>;
    if (typeof q !== 'string' || q.trim().length < 1 || q.trim().length > 140) throw new Error(`question ${i + 1}: bad text`);
    if (!Array.isArray(options) || options.length < 2 || options.length > 4) throw new Error(`question ${i + 1}: needs 2 to 4 options`);
    const cleanOptions = options.map((o, j) => {
      if (typeof o !== 'string' || o.trim().length < 1 || o.trim().length > 80) throw new Error(`question ${i + 1}, option ${j + 1}: bad text`);
      return o.trim();
    });
    if (typeof correct !== 'number' || !Number.isInteger(correct) || correct < 0 || correct >= cleanOptions.length) {
      throw new Error(`question ${i + 1}: bad correct index`);
    }
    return { q: q.trim(), options: cleanOptions, correct };
  });
}

/** Checks the shape of a taker's answers against the questions; throws if the count or any index is out of range. */
export function sanitizeAnswers(questions: QuizQuestion[], input: unknown): number[] {
  if (!Array.isArray(input) || input.length !== questions.length) throw new Error('one answer per question is required');
  return input.map((a, i) => {
    if (typeof a !== 'number' || !Number.isInteger(a) || a < 0 || a >= questions[i].options.length) {
      throw new Error(`answer ${i + 1} is out of range`);
    }
    return a;
  });
}

export function scoreAnswers(questions: QuizQuestion[], answers: number[]): number {
  return questions.reduce((n, q, i) => n + (answers[i] === q.correct ? 1 : 0), 0);
}
