import { BadRequestException, ForbiddenException, Inject, Injectable } from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import {
  REALTIME_BROADCASTER,
  RealtimeBroadcaster,
} from '../realtime/realtime-broadcaster.interface';
import { CanvasRepository } from './canvas.repository';
import { MAX_STROKES_PER_CANVAS, Stroke, sanitizeStrokes } from './strokes';

@Injectable()
export class CanvasService {
  constructor(
    private readonly canvasRepository: CanvasRepository,
    private readonly conversationsRepository: ConversationsRepository,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
  ) {}

  async get(conversationId: string, userId: string): Promise<{ strokes: Stroke[] }> {
    await this.assertDirectMember(conversationId, userId);
    return { strokes: await this.canvasRepository.get(conversationId) };
  }

  async append(conversationId: string, userId: string, raw: unknown[]): Promise<{ count: number }> {
    await this.assertDirectMember(conversationId, userId);
    let clean: Stroke[];
    try {
      clean = sanitizeStrokes(raw);
    } catch (e) {
      throw new BadRequestException((e as Error).message);
    }
    const existing = (await this.canvasRepository.get(conversationId)).length;
    if (existing + clean.length > MAX_STROKES_PER_CANVAS) {
      throw new BadRequestException('The canvas is full — clear it to keep drawing');
    }
    const count = await this.canvasRepository.append(
      conversationId,
      clean.map((s) => ({ ...s, by: userId })),
    );
    await this.notifyChanged(conversationId, userId);
    return { count };
  }

  async clear(conversationId: string, userId: string): Promise<void> {
    await this.assertDirectMember(conversationId, userId);
    await this.canvasRepository.clear(conversationId);
    await this.notifyChanged(conversationId, userId);
  }

  private async notifyChanged(conversationId: string, userId: string): Promise<void> {
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'canvas.changed', conversationId });
  }

  private async assertDirectMember(conversationId: string, userId: string): Promise<void> {
    if (!(await this.conversationsRepository.isMember(conversationId, userId))) {
      throw new ForbiddenException('Not a member of this conversation');
    }
    if (await this.conversationsRepository.isGroup(conversationId)) {
      throw new BadRequestException('The shared canvas is only available in direct conversations');
    }
  }
}
