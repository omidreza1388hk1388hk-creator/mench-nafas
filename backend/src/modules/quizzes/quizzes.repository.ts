import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';
import { QuizQuestion } from './quiz';

export interface QuizRow {
  id: string;
  conversation_id: string;
  author_id: string;
  title: string;
  questions: QuizQuestion[];
  created_at: Date;
}

export interface QuizResultRow {
  quiz_id: string;
  user_id: string;
  answers: number[];
  score: number;
  total: number;
}

@Injectable()
export class QuizzesRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async list(conversationId: string): Promise<QuizRow[]> {
    const { rows } = await this.pool.query<QuizRow>(
      'SELECT * FROM couple_quizzes WHERE conversation_id = $1 ORDER BY created_at DESC',
      [conversationId],
    );
    return rows;
  }

  async results(conversationId: string): Promise<QuizResultRow[]> {
    const { rows } = await this.pool.query<QuizResultRow>(
      `SELECT r.quiz_id, r.user_id, r.answers, r.score, r.total
         FROM couple_quiz_results r JOIN couple_quizzes q ON q.id = r.quiz_id
        WHERE q.conversation_id = $1`,
      [conversationId],
    );
    return rows;
  }

  async countByAuthor(conversationId: string, authorId: string): Promise<number> {
    const { rows } = await this.pool.query<{ n: string }>(
      'SELECT COUNT(*)::text AS n FROM couple_quizzes WHERE conversation_id = $1 AND author_id = $2',
      [conversationId, authorId],
    );
    return Number(rows[0].n);
  }

  async create(conversationId: string, authorId: string, title: string, questions: QuizQuestion[]): Promise<QuizRow> {
    const { rows } = await this.pool.query<QuizRow>(
      'INSERT INTO couple_quizzes (conversation_id, author_id, title, questions) VALUES ($1, $2, $3, $4::jsonb) RETURNING *',
      [conversationId, authorId, title, JSON.stringify(questions)],
    );
    return rows[0];
  }

  async findById(quizId: string): Promise<QuizRow | null> {
    const { rows } = await this.pool.query<QuizRow>('SELECT * FROM couple_quizzes WHERE id = $1', [quizId]);
    return rows[0] ?? null;
  }

  async findResult(quizId: string, userId: string): Promise<QuizResultRow | null> {
    const { rows } = await this.pool.query<QuizResultRow>(
      'SELECT quiz_id, user_id, answers, score, total FROM couple_quiz_results WHERE quiz_id = $1 AND user_id = $2',
      [quizId, userId],
    );
    return rows[0] ?? null;
  }

  /** Returns false if this person already answered (the primary key makes the insert a no-op). */
  async saveResult(quizId: string, userId: string, answers: number[], score: number, total: number): Promise<boolean> {
    const { rowCount } = await this.pool.query(
      `INSERT INTO couple_quiz_results (quiz_id, user_id, answers, score, total) VALUES ($1, $2, $3::jsonb, $4, $5)
       ON CONFLICT DO NOTHING`,
      [quizId, userId, JSON.stringify(answers), score, total],
    );
    return (rowCount ?? 0) > 0;
  }

  async delete(quizId: string): Promise<void> {
    await this.pool.query('DELETE FROM couple_quizzes WHERE id = $1', [quizId]);
  }
}
