import { Module } from '@nestjs/common';
import { CallsRepository } from './calls.repository';

/**
 * Deliberately separate from CallsModule — see the comment atop
 * CallsRepository for why. Only DatabaseModule's PG_POOL provider is
 * needed here (imported globally via DatabaseModule already being
 * @Global in database.module.ts), so this module has no imports of its
 * own and therefore nothing that could reintroduce a cycle.
 */
@Module({
  providers: [CallsRepository],
  exports: [CallsRepository],
})
export class CallsRepositoryModule {}
