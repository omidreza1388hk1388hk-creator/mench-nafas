import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface OtpChallengeRow {
  id: string;
  phone_e164: string;
  code_hash: string;
  attempt_count: number;
  max_attempts: number;
  created_at: Date;
  expires_at: Date;
  consumed_at: Date | null;
}

@Injectable()
export class OtpRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(
    phoneE164: string,
    codeHash: string,
    maxAttempts: number,
    expiresAt: Date,
  ): Promise<OtpChallengeRow> {
    const { rows } = await this.pool.query<OtpChallengeRow>(
      `INSERT INTO otp_challenges (phone_e164, code_hash, max_attempts, expires_at)
       VALUES ($1, $2, $3, $4)
       RETURNING *`,
      [phoneE164, codeHash, maxAttempts, expiresAt],
    );
    return rows[0];
  }

  async findActiveById(id: string): Promise<OtpChallengeRow | null> {
    const { rows } = await this.pool.query<OtpChallengeRow>(
      `SELECT * FROM otp_challenges
       WHERE id = $1 AND consumed_at IS NULL AND expires_at > now()`,
      [id],
    );
    return rows[0] ?? null;
  }

  async incrementAttempts(id: string): Promise<void> {
    await this.pool.query(
      `UPDATE otp_challenges SET attempt_count = attempt_count + 1 WHERE id = $1`,
      [id],
    );
  }

  async consume(id: string): Promise<void> {
    await this.pool.query(
      `UPDATE otp_challenges SET consumed_at = now() WHERE id = $1`,
      [id],
    );
  }

  async mostRecentForPhone(phoneE164: string): Promise<OtpChallengeRow | null> {
    const { rows } = await this.pool.query<OtpChallengeRow>(
      `SELECT * FROM otp_challenges WHERE phone_e164 = $1 ORDER BY created_at DESC LIMIT 1`,
      [phoneE164],
    );
    return rows[0] ?? null;
  }
}
