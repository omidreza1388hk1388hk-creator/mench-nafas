import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface TwoStepRow {
  user_id: string;
  password_hash: string;
  hint: string | null;
  failed_attempts: number;
  locked_until: Date | null;
}

export interface TicketRow {
  id: string;
  user_id: string;
  device_name: string;
  attempts: number;
  expires_at: Date;
}

@Injectable()
export class TwoStepRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async get(userId: string): Promise<TwoStepRow | null> {
    const { rows } = await this.pool.query<TwoStepRow>('SELECT * FROM two_step WHERE user_id = $1', [userId]);
    return rows[0] ?? null;
  }

  async upsert(userId: string, passwordHash: string, hint: string | null): Promise<void> {
    await this.pool.query(
      `INSERT INTO two_step (user_id, password_hash, hint) VALUES ($1, $2, $3)
       ON CONFLICT (user_id) DO UPDATE
         SET password_hash = EXCLUDED.password_hash, hint = EXCLUDED.hint,
             failed_attempts = 0, locked_until = NULL, updated_at = now()`,
      [userId, passwordHash, hint],
    );
  }

  async remove(userId: string): Promise<void> {
    await this.pool.query('DELETE FROM two_step WHERE user_id = $1', [userId]);
    await this.pool.query('DELETE FROM two_step_tickets WHERE user_id = $1', [userId]);
  }

  /** Atomically bumps the counter and returns the new value. */
  async recordFailure(userId: string, lockedUntil: (attempts: number) => Date | null): Promise<void> {
    const { rows } = await this.pool.query<{ failed_attempts: number }>(
      'UPDATE two_step SET failed_attempts = failed_attempts + 1 WHERE user_id = $1 RETURNING failed_attempts',
      [userId],
    );
    const attempts = rows[0]?.failed_attempts ?? 0;
    const until = lockedUntil(attempts);
    if (until) {
      await this.pool.query('UPDATE two_step SET locked_until = $2 WHERE user_id = $1', [userId, until]);
    }
  }

  async recordSuccess(userId: string): Promise<void> {
    await this.pool.query('UPDATE two_step SET failed_attempts = 0, locked_until = NULL WHERE user_id = $1', [userId]);
  }

  async createTicket(userId: string, deviceName: string, expiresAt: Date): Promise<string> {
    // Opportunistic cleanup keeps the table tiny without a separate job.
    await this.pool.query('DELETE FROM two_step_tickets WHERE expires_at < now()');
    const { rows } = await this.pool.query<{ id: string }>(
      'INSERT INTO two_step_tickets (user_id, device_name, expires_at) VALUES ($1, $2, $3) RETURNING id',
      [userId, deviceName, expiresAt],
    );
    return rows[0].id;
  }

  async getTicket(id: string): Promise<TicketRow | null> {
    const { rows } = await this.pool.query<TicketRow>('SELECT * FROM two_step_tickets WHERE id = $1', [id]);
    return rows[0] ?? null;
  }

  async bumpTicket(id: string): Promise<void> {
    await this.pool.query('UPDATE two_step_tickets SET attempts = attempts + 1 WHERE id = $1', [id]);
  }

  async deleteTicket(id: string): Promise<void> {
    await this.pool.query('DELETE FROM two_step_tickets WHERE id = $1', [id]);
  }
}
