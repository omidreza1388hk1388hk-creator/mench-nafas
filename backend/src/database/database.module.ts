import { Global, Injectable, Module, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Pool } from 'pg';

export const PG_POOL = 'PG_POOL';

// Same reasoning as RedisConnection in redis.module.ts: wrapping the pool
// in its own provider lets Nest call onModuleDestroy and actually drain
// the pg Pool on app.close(), instead of leaving open sockets that keep
// the process (and Jest, in e2e tests) alive after the test run finishes.
@Injectable()
class PgPoolConnection implements OnModuleDestroy {
  readonly pool: Pool;

  constructor(config: ConfigService) {
    this.pool = new Pool({
      connectionString: config.get<string>('DATABASE_URL'),
      max: 10,
      idleTimeoutMillis: 30_000,
    });
  }

  async onModuleDestroy() {
    await this.pool.end();
  }
}

@Global()
@Module({
  providers: [
    PgPoolConnection,
    {
      provide: PG_POOL,
      inject: [PgPoolConnection],
      useFactory: (connection: PgPoolConnection): Pool => connection.pool,
    },
  ],
  exports: [PG_POOL],
})
export class DatabaseModule {}
