import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common';
import { ConversationsService } from '../conversations/conversations.service';
import { MessagesService } from '../messages/messages.service';
import { ScheduledMessageDto } from './scheduled-message.dto';
import { ScheduledMessagesRepository, toScheduledDto } from './scheduled-messages.repository';

export const MIN_LEAD_MS = 10_000;
export const MAX_LEAD_MS = 365 * 24 * 60 * 60 * 1000;
export const MAX_PENDING_PER_CONVERSATION = 50;
const TICK_MS = 15_000;
const BATCH = 20;
const STUCK_AFTER_SECONDS = 120;

/** Validates a requested send time against "now"; returns the Date or throws a 400. Pure, so it is unit-tested. */
export function parseSendAt(raw: string, now: number): Date {
  const at = new Date(raw);
  if (Number.isNaN(at.getTime())) throw new BadRequestException('sendAt is not a valid time');
  if (at.getTime() < now + MIN_LEAD_MS)
    throw new BadRequestException('Pick a time at least a few seconds in the future');
  if (at.getTime() > now + MAX_LEAD_MS)
    throw new BadRequestException('Messages can be scheduled at most one year ahead');
  return at;
}

@Injectable()
export class ScheduledMessagesService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger('ScheduledMessagesService');
  private timer: NodeJS.Timeout | null = null;
  private ticking = false;

  constructor(
    private readonly repository: ScheduledMessagesRepository,
    private readonly conversationsService: ConversationsService,
    private readonly messagesService: MessagesService,
    private readonly nowFn: () => number = () => Date.now(),
  ) {}

  onModuleInit(): void {
    this.timer = setInterval(() => void this.tick(), TICK_MS);
    this.timer.unref?.();
  }

  onModuleDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  async schedule(input: {
    conversationId: string;
    senderId: string;
    clientMsgId: string;
    body: string;
    sendAt: string;
    silent?: boolean;
  }): Promise<ScheduledMessageDto> {
    await this.conversationsService.assertMember(input.conversationId, input.senderId);
    const body = input.body.trim();
    if (!body) throw new BadRequestException('Message is empty');
    const sendAt = parseSendAt(input.sendAt, this.nowFn());
    if (
      (await this.repository.countPending(input.conversationId, input.senderId)) >=
      MAX_PENDING_PER_CONVERSATION
    ) {
      throw new BadRequestException('Too many scheduled messages in this chat');
    }
    const row = await this.repository.create({
      conversationId: input.conversationId,
      senderId: input.senderId,
      clientMsgId: input.clientMsgId,
      body,
      silent: input.silent === true,
      sendAt,
    });
    return toScheduledDto(row);
  }

  async list(conversationId: string, senderId: string): Promise<ScheduledMessageDto[]> {
    await this.conversationsService.assertMember(conversationId, senderId);
    return (await this.repository.listPending(conversationId, senderId)).map(toScheduledDto);
  }

  async cancel(id: string, senderId: string): Promise<void> {
    if (!(await this.repository.deletePending(id, senderId)))
      throw new NotFoundException('Scheduled message not found');
  }

  /** One worker pass. Re-entrancy guarded so a slow batch never overlaps the next tick. */
  async tick(): Promise<number> {
    if (this.ticking) return 0;
    this.ticking = true;
    let sent = 0;
    try {
      await this.repository.releaseStuck(STUCK_AFTER_SECONDS);
      const due = await this.repository.claimDue(BATCH);
      for (const row of due) {
        try {
          await this.messagesService.send({
            conversationId: row.conversation_id,
            senderId: row.sender_id,
            clientMsgId: row.client_msg_id,
            body: row.body,
            silent: row.silent,
          });
          await this.repository.markSent(row.id);
          sent++;
        } catch (err) {
          this.logger.warn(`Scheduled message ${row.id} failed: ${(err as Error).message}`);
          await this.repository.markFailed(row.id, (err as Error).message);
        }
      }
    } catch (err) {
      this.logger.warn(`Scheduled-message tick failed: ${(err as Error).message}`);
    } finally {
      this.ticking = false;
    }
    return sent;
  }
}
