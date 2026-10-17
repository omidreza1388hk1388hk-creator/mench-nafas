import {
  BadRequestException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import {
  REALTIME_BROADCASTER,
  RealtimeBroadcaster,
} from '../realtime/realtime-broadcaster.interface';
import { CoupleRepository, WishRow } from './couple.repository';
import { iranDateString, todaysQuestion } from './questions';
import { computeStreak } from './streak';

export interface WishDto {
  id: string;
  title: string;
  done: boolean;
  doneAt: string | null;
  createdAt: string;
  isMine: boolean;
}

const toWishDto = (r: WishRow, viewerId: string): WishDto => ({
  id: r.id,
  title: r.title,
  done: r.done,
  doneAt: r.done_at ? r.done_at.toISOString() : null,
  createdAt: r.created_at.toISOString(),
  isMine: r.author_id === viewerId,
});

@Injectable()
export class CoupleService {
  constructor(
    private readonly coupleRepository: CoupleRepository,
    private readonly conversationsRepository: ConversationsRepository,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
  ) {}

  // ---- question of the day ------------------------------------------------

  /** The partner's answer is only revealed once YOU have answered too. */
  async getQuestion(conversationId: string, userId: string) {
    await this.assertDirectMember(conversationId, userId);
    const date = iranDateString();
    const answers = await this.coupleRepository.listAnswers(conversationId, date);
    const mine = answers.find((a) => a.user_id === userId)?.answer ?? null;
    const partner = answers.find((a) => a.user_id !== userId)?.answer ?? null;
    const streak = computeStreak(
      await this.coupleRepository.listBothAnsweredDates(conversationId),
      date,
    );
    return {
      streak: streak.current,
      bestStreak: streak.best,
      date,
      question: todaysQuestion(conversationId, date),
      myAnswer: mine,
      partnerAnswered: partner !== null,
      partnerAnswer: mine !== null ? partner : null,
    };
  }

  async answerQuestion(conversationId: string, userId: string, answer: string): Promise<void> {
    await this.assertDirectMember(conversationId, userId);
    await this.coupleRepository.upsertAnswer(
      conversationId,
      iranDateString(),
      userId,
      answer.trim(),
    );
    const others = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(others, {
      type: 'question.answered',
      conversationId,
    });
  }

  // ---- wishlist -----------------------------------------------------------

  async listWishes(conversationId: string, userId: string): Promise<WishDto[]> {
    await this.assertDirectMember(conversationId, userId);
    return (await this.coupleRepository.listWishes(conversationId)).map((r) =>
      toWishDto(r, userId),
    );
  }

  async createWish(conversationId: string, userId: string, title: string): Promise<WishDto> {
    await this.assertDirectMember(conversationId, userId);
    const row = await this.coupleRepository.createWish(conversationId, userId, title.trim());
    await this.notifyWishlistChanged(conversationId, userId);
    return toWishDto(row, userId);
  }

  async updateWish(
    conversationId: string,
    wishId: string,
    userId: string,
    done: boolean,
  ): Promise<WishDto> {
    await this.assertDirectMember(conversationId, userId);
    const wish = await this.requireWish(conversationId, wishId);
    const row = await this.coupleRepository.setWishDone(wish.id, done);
    await this.notifyWishlistChanged(conversationId, userId);
    return toWishDto(row, userId);
  }

  async deleteWish(conversationId: string, wishId: string, userId: string): Promise<void> {
    await this.assertDirectMember(conversationId, userId);
    const wish = await this.requireWish(conversationId, wishId);
    if (wish.author_id !== userId)
      throw new ForbiddenException('Only the author can delete a wish');
    await this.coupleRepository.deleteWish(wish.id);
    await this.notifyWishlistChanged(conversationId, userId);
  }

  private async requireWish(conversationId: string, wishId: string): Promise<WishRow> {
    const wish = await this.coupleRepository.findWish(wishId);
    if (!wish || wish.conversation_id !== conversationId)
      throw new NotFoundException('Wish not found');
    return wish;
  }

  private async notifyWishlistChanged(conversationId: string, userId: string): Promise<void> {
    const others = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(others, { type: 'wishlist.changed', conversationId });
  }

  private async assertDirectMember(conversationId: string, userId: string): Promise<void> {
    if (!(await this.conversationsRepository.isMember(conversationId, userId))) {
      throw new ForbiddenException('Not a member of this conversation');
    }
    if (await this.conversationsRepository.isGroup(conversationId)) {
      throw new BadRequestException('Only available in direct conversations');
    }
  }
}
