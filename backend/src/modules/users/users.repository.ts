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
  /** ms timestamp of the current profile photo (BIGINT arrives from pg as a string); null = none. */
  avatar_version: string | null;
  bio: string | null;
  notification_privacy_mode: NotificationPrivacyMode;
  /** Phase 1 completion: false hides this user from contact-sync lookups (optional so older fixtures still type-check). */
  discoverable_by_phone?: boolean;
  created_at: Date;
  updated_at: Date;
}

@Injectable()
export class UsersRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async findByPhone(phoneE164: string): Promise<UserRow | null> {
    const { rows } = await this.pool.query<UserRow>('SELECT * FROM users WHERE phone_e164 = $1', [
      phoneE164,
    ]);
    return rows[0] ?? null;
  }

  async findById(id: string): Promise<UserRow | null> {
    const { rows } = await this.pool.query<UserRow>('SELECT * FROM users WHERE id = $1', [id]);
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

  /** Contact-sync's counterpart to findManyByIds — same batching reasoning, keyed by phone number instead of id since that's what a device address book has, not user ids. */
  async findManyByPhones(phones: string[]): Promise<UserRow[]> {
    if (phones.length === 0) return [];
    const { rows } = await this.pool.query<UserRow>(
      // Users who turned off "let contacts find me" are never revealed by a contact-sync batch.
      'SELECT * FROM users WHERE phone_e164 = ANY($1::text[]) AND discoverable_by_phone = true AND deleted_at IS NULL',
      [phones],
    );
    return rows;
  }

  async getContactPrivacy(userId: string): Promise<boolean> {
    const { rows } = await this.pool.query<{ discoverable_by_phone: boolean }>(
      'SELECT discoverable_by_phone FROM users WHERE id = $1',
      [userId],
    );
    return rows[0]?.discoverable_by_phone ?? true;
  }

  async setContactPrivacy(userId: string, discoverable: boolean): Promise<void> {
    await this.pool.query(
      'UPDATE users SET discoverable_by_phone = $2, updated_at = now() WHERE id = $1',
      [userId, discoverable],
    );
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
  async setAvatarVersion(userId: string, version: number): Promise<void> {
    await this.pool.query(
      'UPDATE users SET avatar_version = $2, updated_at = now() WHERE id = $1',
      [userId, version],
    );
  }

  /** True when both users are members of at least one common conversation (or are the same user). */
  async setE2eeKey(userId: string, publicKey: string): Promise<void> {
    await this.pool.query(
      'UPDATE users SET e2ee_public_key = $2, updated_at = now() WHERE id = $1',
      [userId, publicKey],
    );
  }

  async getE2eeKey(userId: string): Promise<string | null> {
    const { rows } = await this.pool.query<{ e2ee_public_key: string | null }>(
      'SELECT e2ee_public_key FROM users WHERE id = $1',
      [userId],
    );
    return rows[0]?.e2ee_public_key ?? null;
  }

  async sharesConversation(userA: string, userB: string): Promise<boolean> {
    if (userA === userB) return true;
    const { rows } = await this.pool.query(
      `SELECT 1 FROM conversation_members a
         JOIN conversation_members b ON a.conversation_id = b.conversation_id
        WHERE a.user_id = $1 AND b.user_id = $2 LIMIT 1`,
      [userA, userB],
    );
    return rows.length > 0;
  }

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
  async updateNotificationPrivacyMode(
    userId: string,
    mode: NotificationPrivacyMode,
  ): Promise<void> {
    await this.pool.query(
      `UPDATE users SET notification_privacy_mode = $2, updated_at = now() WHERE id = $1`,
      [userId, mode],
    );
  }
}
