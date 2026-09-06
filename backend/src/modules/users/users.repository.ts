import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export type NotificationPrivacyMode = 'full_content' | 'sender_only' | 'hide_content';

export interface UserRow {
  id: string;
  phone_e164: string;
  display_name: string | null;
  username: string | null;
  avatar_url: string | null;
  bio: string | null;
  notification_privacy_mode: NotificationPrivacyMode;
  created_at: Date;
  updated_at: Date;
}

@Injectable()
export class UsersRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async findByPhone(phoneE164: string): Promise<UserRow | null> {
    const { rows } = await this.pool.query<UserRow>(
      'SELECT * FROM users WHERE phone_e164 = $1',
      [phoneE164],
    );
    return rows[0] ?? null;
  }

  async findById(id: string): Promise<UserRow | null> {
    const { rows } = await this.pool.query<UserRow>(
      'SELECT * FROM users WHERE id = $1',
      [id],
    );
    return rows[0] ?? null;
  }

  /** Batched — used by ConversationsService.createGroup to validate every proposed member in one round trip rather than one findById per id. */
  async findManyByIds(ids: string[]): Promise<UserRow[]> {
    if (ids.length === 0) return [];
    const { rows } = await this.pool.query<UserRow>(
      'SELECT * FROM users WHERE id = ANY($1::uuid[])',
      [ids],
    );
    return rows;
  }

  /** Creates the user if the phone number is new, otherwise returns the existing row (atomic, race-safe). */
  async findOrCreateByPhone(phoneE164: string): Promise<UserRow> {
    const { rows } = await this.pool.query<UserRow>(
      `INSERT INTO users (phone_e164) VALUES ($1)
       ON CONFLICT (phone_e164) DO UPDATE SET phone_e164 = EXCLUDED.phone_e164
       RETURNING *`,
      [phoneE164],
    );
    return rows[0];
  }

  /**
   * Builds the SET clause from only the fields actually present in patch,
   * so a partial update (e.g. bio only) never overwrites displayName/
   * username with NULL. updated_at is bumped unconditionally alongside
   * whatever did change.
   */
  async updateProfile(
    userId: string,
    patch: { displayName?: string; username?: string; bio?: string },
  ): Promise<UserRow> {
    const setClauses: string[] = [];
    const values: unknown[] = [];

    for (const [column, value] of Object.entries({
      display_name: patch.displayName,
      username: patch.username,
      bio: patch.bio,
    })) {
      if (value !== undefined) {
        values.push(value);
        setClauses.push(`${column} = $${values.length}`);
      }
    }
    setClauses.push('updated_at = now()');

    values.push(userId);
    const { rows } = await this.pool.query<UserRow>(
      `UPDATE users SET ${setClauses.join(', ')} WHERE id = $${values.length} RETURNING *`,
      values,
    );
    return rows[0];
  }

  /** Read by NotificationsService before building a push payload — see that service's buildPayload for why this must always be read from the RECIPIENT, never the sender. */
  async updateNotificationPrivacyMode(userId: string, mode: NotificationPrivacyMode): Promise<void> {
    await this.pool.query(
      `UPDATE users SET notification_privacy_mode = $2, updated_at = now() WHERE id = $1`,
      [userId, mode],
    );
  }
}
