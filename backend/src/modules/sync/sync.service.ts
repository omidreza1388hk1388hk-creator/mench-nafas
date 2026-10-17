import { BadRequestException, Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

const KEY_PATTERN = /^(draft|setting):[A-Za-z0-9._-]{1,80}$/;
const MAX_VALUE_BYTES = 8 * 1024;

export interface SyncEntry {
  key: string;
  value: string;
  updatedAt: string;
}

/** Small per-user key/value store, last write wins. Only `draft:` and `setting:` keys are accepted. */
@Injectable()
export class SyncService {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async get(userId: string, key: string): Promise<SyncEntry | null> {
    this.assertKey(key);
    const { rows } = await this.pool.query(
      'SELECT key, value, updated_at FROM sync_state WHERE user_id = $1 AND key = $2',
      [userId, key],
    );
    return rows[0] ? this.toEntry(rows[0]) : null;
  }

  /** Entries changed after `since` (ISO) so a device can catch up without re-downloading everything. */
  async changedSince(userId: string, since?: string): Promise<SyncEntry[]> {
    const sinceDate = since ? new Date(since) : new Date(0);
    if (Number.isNaN(sinceDate.getTime())) throw new BadRequestException('Invalid since');
    const { rows } = await this.pool.query(
      'SELECT key, value, updated_at FROM sync_state WHERE user_id = $1 AND updated_at > $2 ORDER BY updated_at ASC LIMIT 500',
      [userId, sinceDate],
    );
    return rows.map((r) => this.toEntry(r));
  }

  async put(userId: string, key: string, value: string): Promise<SyncEntry> {
    this.assertKey(key);
    if (Buffer.byteLength(value, 'utf8') > MAX_VALUE_BYTES)
      throw new BadRequestException('Value too large');
    const { rows } = await this.pool.query(
      `INSERT INTO sync_state (user_id, key, value) VALUES ($1, $2, $3)
       ON CONFLICT (user_id, key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()
       RETURNING key, value, updated_at`,
      [userId, key, value],
    );
    return this.toEntry(rows[0]);
  }

  async remove(userId: string, key: string): Promise<void> {
    this.assertKey(key);
    await this.pool.query('DELETE FROM sync_state WHERE user_id = $1 AND key = $2', [userId, key]);
  }

  private assertKey(key: string) {
    if (!KEY_PATTERN.test(key)) throw new BadRequestException('Invalid key');
  }

  private toEntry(r: { key: string; value: string; updated_at: Date }): SyncEntry {
    return { key: r.key, value: r.value, updatedAt: r.updated_at.toISOString() };
  }
}
