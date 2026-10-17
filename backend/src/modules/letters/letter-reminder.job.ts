import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { NotificationsService } from '../notifications/notifications.service';
import { UsersService } from '../users/users.service';
import { LettersRepository } from './letters.repository';

/**
 * Phase 3A — every minute, finds time-capsule letters whose unlock time has just passed and pushes
 * "your letter is ready" to the recipient (once; the claim is atomic in the database). Failures are logged, never thrown,
 * and a failed tick simply tries again a minute later.
 */
@Injectable()
export class LetterReminderJob implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger('LetterReminderJob');
  private timer: NodeJS.Timeout | null = null;
  private running = false;

  constructor(
    private readonly lettersRepository: LettersRepository,
    private readonly conversationsRepository: ConversationsRepository,
    private readonly usersService: UsersService,
    private readonly notificationsService: NotificationsService,
  ) {}

  onModuleInit(): void {
    // Not started under test runners, so unit tests never leave a timer behind.
    if (process.env.NODE_ENV === 'test') return;
    this.timer = setInterval(() => void this.tick(), 60_000);
    this.timer.unref?.();
  }

  onModuleDestroy(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  /** One pass; public so it can be tested directly. Returns how many reminders were sent. */
  async tick(): Promise<number> {
    if (this.running) return 0; // never overlap two passes
    this.running = true;
    try {
      const due = await this.lettersRepository.claimDueReminders(50);
      for (const letter of due) {
        const recipients = await this.conversationsRepository.listOtherMemberIds(letter.conversation_id, letter.author_id);
        const sender = await this.usersService.findById(letter.author_id);
        await this.notificationsService.notifyLetterUnlocked({
          recipientUserIds: recipients,
          conversationId: letter.conversation_id,
          senderDisplayName: sender?.display_name ?? sender?.phone_e164 ?? 'Someone',
        });
      }
      return due.length;
    } catch (err) {
      this.logger.warn(`reminder pass failed: ${(err as Error).message}`);
      return 0;
    } finally {
      this.running = false;
    }
  }
}
