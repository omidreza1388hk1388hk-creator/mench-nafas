import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface SessionRow {
  id: string;
  user_id: string;
  device_id: string;
  refresh_token_hash: string;
  issued_at: Date;
  expires_at: Date;
  revoked_at: Date | null;
  replaced_by_id: string | null;
}

@Injectable()
export class SessionsRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(
    userId: string,
    deviceId: string,
    refreshTokenHash: string,
    expiresAt: Date,
  ): Promise<SessionRow> {
    const { rows } = await this.pool.query<SessionRow>(
      `INSERT INTO sessions (user_id, device_id, refresh_token_hash, expires_at)
       VALUES ($1, $2, $3, $4)
       RETURNING *`,
      [userId, deviceId, refreshTokenHash, expiresAt],
    );
    return rows[0];
  }

  async findActiveByTokenHash(refreshTokenHash: string): Promise<SessionRow | null> {
    const { rows } = await this.pool.query<SessionRow>(
      `SELECT * FROM sessions
       WHERE refresh_token_hash = $1 AND revoked_at IS NULL AND expires_at > now()`,
      [refreshTokenHash],
    );
    return rows[0] ?? null;
  }

  /** Revokes the old session and links it to its replacement (rotation, not reuse). */
  async rotate(oldSessionId: string, newSessionId: string): Promise<void> {
    await this.pool.query(
      `UPDATE sessions SET revoked_at = now(), replaced_by_id = $2 WHERE id = $1`,
      [oldSessionId, newSessionId],
    );
  }

  async revoke(sessionId: string, userId: string): Promise<void> {
    await this.pool.query(
      `UPDATE sessions SET revoked_at = now() WHERE id = $1 AND user_id = $2`,
      [sessionId, userId],
    );
  }

  async revokeAllForUser(userId: string): Promise<void> {
    await this.pool.query(
      `UPDATE sessions SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL`,
      [userId],
    );
  }

  async listActiveForUser(userId: string): Promise<SessionRow[]> {
    const { rows } = await this.pool.query<SessionRow>(
      `SELECT * FROM sessions WHERE user_id = $1 AND revoked_at IS NULL AND expires_at > now()`,
      [userId],
    );
    return rows;
  }
}
