import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface DeviceRow {
  id: string;
  user_id: string;
  device_name: string;
  platform: string;
  last_seen_at: Date | null;
  created_at: Date;
  revoked_at: Date | null;
  push_token: string | null;
  push_provider: string | null;
  push_token_updated_at: Date | null;
}

@Injectable()
export class DevicesRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(userId: string, deviceName: string, platform: string): Promise<DeviceRow> {
    const { rows } = await this.pool.query<DeviceRow>(
      `INSERT INTO devices (user_id, device_name, platform, last_seen_at)
       VALUES ($1, $2, $3, now())
       RETURNING *`,
      [userId, deviceName, platform],
    );
    return rows[0];
  }

  async listActiveForUser(userId: string): Promise<DeviceRow[]> {
    const { rows } = await this.pool.query<DeviceRow>(
      `SELECT * FROM devices WHERE user_id = $1 AND revoked_at IS NULL ORDER BY last_seen_at DESC NULLS LAST`,
      [userId],
    );
    return rows;
  }

  /**
   * Revocation also clears the push token: a revoked device must not keep
   * receiving pushes just because its (now-invalid-for-auth) row still has
   * a token cached from before it was revoked. There is no separate
   * "unregister for push" flow — revocation is the only case that needs
   * to stop pushes deliberately, and it's already the right hook for it.
   */
  async revoke(deviceId: string, userId: string): Promise<void> {
    await this.pool.query(
      `UPDATE devices SET revoked_at = now(), push_token = NULL, push_provider = NULL
       WHERE id = $1 AND user_id = $2`,
      [deviceId, userId],
    );
  }

  /** Ownership-checked lookup — used before trusting a push-token update to belong to the requesting user (404, not 403, matching this codebase's existing information-leak-avoidance convention — see ConversationsService.assertMember). */
  async findByIdForUser(deviceId: string, userId: string): Promise<DeviceRow | null> {
    const { rows } = await this.pool.query<DeviceRow>(
      `SELECT * FROM devices WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL`,
      [deviceId, userId],
    );
    return rows[0] ?? null;
  }

  /** Idempotent: registering the same token again just refreshes push_token_updated_at, which is exactly what the Android client's "re-register on every cold start if the token differs" logic needs. */
  async updatePushToken(deviceId: string, userId: string, token: string, provider: 'fcm'): Promise<void> {
    await this.pool.query(
      `UPDATE devices
       SET push_token = $3, push_provider = $4, push_token_updated_at = now()
       WHERE id = $1 AND user_id = $2`,
      [deviceId, userId, token, provider],
    );
  }

  /** Every non-revoked device for a user that currently has a push token registered — the actual send-fanout list for NotificationsService. */
  async listPushableForUser(userId: string): Promise<DeviceRow[]> {
    const { rows } = await this.pool.query<DeviceRow>(
      `SELECT * FROM devices
       WHERE user_id = $1 AND revoked_at IS NULL AND push_token IS NOT NULL`,
      [userId],
    );
    return rows;
  }
}
