import { Global, Injectable, Module, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

export const REDIS_CLIENT = 'REDIS_CLIENT';

// Wrapped in its own provider (instead of a bare useFactory) specifically
// so Nest tracks it in the module lifecycle graph and calls
// onModuleDestroy — otherwise the ioredis socket is left open after
// app.close(), which is what was causing the process (and Jest, in e2e
// tests) to hang instead of exiting.
@Injectable()
class RedisConnection implements OnModuleDestroy {
  readonly client: Redis;

  constructor(config: ConfigService) {
    this.client = new Redis(config.get<string>('REDIS_URL') as string);
  }

  async onModuleDestroy() {
    await this.client.quit();
  }
}

@Global()
@Module({
  providers: [
    RedisConnection,
    {
      provide: REDIS_CLIENT,
      inject: [RedisConnection],
      useFactory: (connection: RedisConnection): Redis => connection.client,
    },
  ],
  exports: [REDIS_CLIENT],
})
export class RedisModule {}
