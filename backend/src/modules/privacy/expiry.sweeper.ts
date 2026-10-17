import { Inject, Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { PrivacyRepository } from './privacy.repository';
import {
  REALTIME_BROADCASTER,
  RealtimeBroadcaster,
} from '../realtime/realtime-broadcaster.interface';

/**
 * Turns expired disappearing messages into deleted ones and tells everyone online.
 * Runs every 30 s; each pass handles up to 500 rows and loops until nothing is due, so a backlog
 * after downtime drains quickly. Disabled under NODE_ENV=test and via DISAPPEARING_SWEEPER=off.
 */
@Injectable()
export class ExpirySweeper implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(ExpirySweeper.name);
  private timer?: NodeJS.Timeout;
  private running = false;

  constructor(
    private readonly repository: PrivacyRepository,
    @Inject(REALTIME_BROADCASTER) private readonly broadcaster: RealtimeBroadcaster,
  ) {}

  onModuleInit(): void {
    if (process.env.NODE_ENV === 'test' || process.env.DISAPPEARING_SWEEPER === 'off') return;
    this.timer = setInterval(() => void this.sweepOnce(), 30_000);
    this.timer.unref();
  }

  onModuleDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  /** Public so tests (and an admin trigger) can run one pass. Returns how many messages expired. */
  async sweepOnce(): Promise<number> {
    if (this.running) return 0;
    this.running = true;
    let total = 0;
    try {
      for (let pass = 0; pass < 20; pass++) {
        const expired = await this.repository.sweepExpired(500);
        if (expired.length === 0) break;
        total += expired.length;

        const byConversation = new Map<string, typeof expired>();
        for (const row of expired) {
          const list = byConversation.get(row.conversation_id) ?? [];
          list.push(row);
          byConversation.set(row.conversation_id, list);
        }
        for (const [conversationId, rows] of byConversation) {
          const members = await this.repository.memberIds(conversationId);
          for (const row of rows) {
            this.broadcaster.broadcastToUsers(members, {
              type: 'message.deleted',
              conversationId,
              messageId: row.id,
              deletedAt: row.deleted_at.toISOString(),
            });
          }
        }
        if (expired.length < 500) break;
      }
    } catch (err) {
      this.logger.error(`expiry sweep failed: ${(err as Error).message}`);
    } finally {
      this.running = false;
    }
    return total;
  }
}
