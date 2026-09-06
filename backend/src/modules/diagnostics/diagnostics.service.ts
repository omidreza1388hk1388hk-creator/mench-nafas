import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { Redis } from 'ioredis';
import { PG_POOL } from '../../database/database.module';
import { REDIS_CLIENT } from '../../redis/redis.module';
import { STORAGE_PROVIDER } from '../../storage/storage.module';
import { StorageProvider } from '../../storage/storage-provider.interface';

export type CheckStatus = 'pass' | 'warning' | 'fail';

export interface DiagnosticCheck {
  name: string;
  status: CheckStatus;
  /** Never includes secrets/connection strings — see docs/SECURITY.md; only a short human-readable outcome. */
  detail: string;
  latencyMs: number;
}

export interface DiagnosticsReport {
  overall: CheckStatus;
  serverTime: string;
  checks: DiagnosticCheck[];
}

const RESERVED_STORAGE_KEY = 'diagnostics/healthcheck.txt';

/**
 * Every check here is a real round trip to the actual dependency — never
 * a hardcoded "ok" (spec section 40: diagnostics must reflect true
 * status). Each is independently try/caught so one dependency being down
 * (e.g. Redis) doesn't prevent reporting on the others.
 */
@Injectable()
export class DiagnosticsService {
  constructor(
    @Inject(PG_POOL) private readonly pool: Pool,
    @Inject(REDIS_CLIENT) private readonly redis: Redis,
    @Inject(STORAGE_PROVIDER) private readonly storage: StorageProvider,
  ) {}

  async run(): Promise<DiagnosticsReport> {
    const checks = await Promise.all([
      this.checkDatabase(),
      this.checkRedis(),
      this.checkStorage(),
    ]);

    const overall: CheckStatus = checks.some((c) => c.status === 'fail')
      ? 'fail'
      : checks.some((c) => c.status === 'warning')
        ? 'warning'
        : 'pass';

    return { overall, serverTime: new Date().toISOString(), checks };
  }

  private async checkDatabase(): Promise<DiagnosticCheck> {
    const start = Date.now();
    try {
      await this.pool.query('SELECT 1');
      const latencyMs = Date.now() - start;
      return {
        name: 'database',
        status: latencyMs > 1000 ? 'warning' : 'pass',
        detail: latencyMs > 1000 ? 'Connected, but responding slowly' : 'Connected',
        latencyMs,
      };
    } catch {
      return { name: 'database', status: 'fail', detail: 'Could not reach the database', latencyMs: Date.now() - start };
    }
  }

  private async checkRedis(): Promise<DiagnosticCheck> {
    const start = Date.now();
    try {
      await this.redis.ping();
      const latencyMs = Date.now() - start;
      return {
        name: 'redis',
        status: latencyMs > 1000 ? 'warning' : 'pass',
        detail: latencyMs > 1000 ? 'Connected, but responding slowly' : 'Connected',
        latencyMs,
      };
    } catch {
      return { name: 'redis', status: 'fail', detail: 'Could not reach Redis', latencyMs: Date.now() - start };
    }
  }

  /** Real write-then-delete round trip against whichever StorageProvider is actually configured, not just a config-presence check. */
  private async checkStorage(): Promise<DiagnosticCheck> {
    const start = Date.now();
    try {
      await this.storage.put(RESERVED_STORAGE_KEY, Buffer.from('ok'), 'text/plain');
      await this.storage.get(RESERVED_STORAGE_KEY);
      await this.storage.delete(RESERVED_STORAGE_KEY);
      return { name: 'storage', status: 'pass', detail: 'Read/write verified', latencyMs: Date.now() - start };
    } catch {
      return { name: 'storage', status: 'fail', detail: 'Could not write to storage', latencyMs: Date.now() - start };
    }
  }
}
