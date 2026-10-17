import { BadRequestException, ConflictException, ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { REALTIME_BROADCASTER, RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { QuizQuestion, sanitizeAnswers, sanitizeQuestions, scoreAnswers } from './quiz';
import { QuizResultRow, QuizRow, QuizzesRepository } from './quizzes.repository';

export const MAX_QUIZZES_PER_AUTHOR = 10;

export interface QuizSummaryDto {
  id: string;
  title: string;
  isMine: boolean;
  questionCount: number;
  /** Your own result if you answered (someone else's quiz). */
  myScore: number | null;
  myTotal: number | null;
  /** The partner's result on a quiz you wrote. */
  partnerScore: number | null;
  partnerTotal: number | null;
}

export interface QuizDetailDto {
  id: string;
  title: string;
  isMine: boolean;
  answered: boolean;
  /** `correct` is only sent to the author, or to someone who has already answered. */
  questions: { q: string; options: string[]; correct: number | null }[];
}

@Injectable()
export class QuizzesService {
  constructor(
    private readonly quizzesRepository: QuizzesRepository,
    private readonly conversationsRepository: ConversationsRepository,
    @Inject(REALTIME_BROADCASTER) private readonly realtimeBroadcaster: RealtimeBroadcaster,
  ) {}

  async list(conversationId: string, userId: string): Promise<QuizSummaryDto[]> {
    await this.assertDirectMember(conversationId, userId);
    const [quizzes, results] = await Promise.all([
      this.quizzesRepository.list(conversationId),
      this.quizzesRepository.results(conversationId),
    ]);
    return quizzes.map((q) => {
      const mine = q.author_id === userId;
      const forQuiz = results.filter((r) => r.quiz_id === q.id);
      const mineRes = forQuiz.find((r) => r.user_id === userId) ?? null;
      const theirRes = forQuiz.find((r) => r.user_id !== userId) ?? null;
      return {
        id: q.id,
        title: q.title,
        isMine: mine,
        questionCount: q.questions.length,
        myScore: !mine && mineRes ? mineRes.score : null,
        myTotal: !mine && mineRes ? mineRes.total : null,
        partnerScore: mine && theirRes ? theirRes.score : null,
        partnerTotal: mine && theirRes ? theirRes.total : null,
      };
    });
  }

  async create(conversationId: string, userId: string, dto: { title: string; questions: unknown[] }): Promise<QuizSummaryDto> {
    await this.assertDirectMember(conversationId, userId);
    let questions: QuizQuestion[];
    try {
      questions = sanitizeQuestions(dto.questions);
    } catch (e) {
      throw new BadRequestException((e as Error).message);
    }
    if ((await this.quizzesRepository.countByAuthor(conversationId, userId)) >= MAX_QUIZZES_PER_AUTHOR) {
      throw new BadRequestException(`At most ${MAX_QUIZZES_PER_AUTHOR} quizzes per person`);
    }
    const row = await this.quizzesRepository.create(conversationId, userId, dto.title.trim(), questions);
    await this.notifyChanged(conversationId, userId);
    return { id: row.id, title: row.title, isMine: true, questionCount: questions.length, myScore: null, myTotal: null, partnerScore: null, partnerTotal: null };
  }

  async get(conversationId: string, quizId: string, userId: string): Promise<QuizDetailDto> {
    await this.assertDirectMember(conversationId, userId);
    const quiz = await this.findInConversation(conversationId, quizId);
    const mine = quiz.author_id === userId;
    const answered = !mine && (await this.quizzesRepository.findResult(quizId, userId)) !== null;
    const reveal = mine || answered;
    return {
      id: quiz.id,
      title: quiz.title,
      isMine: mine,
      answered,
      questions: quiz.questions.map((q) => ({ q: q.q, options: q.options, correct: reveal ? q.correct : null })),
    };
  }

  async answer(conversationId: string, quizId: string, userId: string, raw: unknown[]): Promise<{ score: number; total: number; correct: number[] }> {
    await this.assertDirectMember(conversationId, userId);
    const quiz = await this.findInConversation(conversationId, quizId);
    if (quiz.author_id === userId) throw new BadRequestException('You cannot answer your own quiz');
    let answers: number[];
    try {
      answers = sanitizeAnswers(quiz.questions, raw);
    } catch (e) {
      throw new BadRequestException((e as Error).message);
    }
    const score = scoreAnswers(quiz.questions, answers);
    const saved = await this.quizzesRepository.saveResult(quizId, userId, answers, score, quiz.questions.length);
    if (!saved) throw new ConflictException('You already answered this quiz');
    this.realtimeBroadcaster.broadcastToUsers([quiz.author_id], { type: 'quiz.changed', conversationId });
    return { score, total: quiz.questions.length, correct: quiz.questions.map((q) => q.correct) };
  }

  async remove(conversationId: string, quizId: string, userId: string): Promise<void> {
    await this.assertDirectMember(conversationId, userId);
    const quiz = await this.findInConversation(conversationId, quizId);
    if (quiz.author_id !== userId) throw new ForbiddenException('Only the author can delete a quiz');
    await this.quizzesRepository.delete(quizId);
    await this.notifyChanged(conversationId, userId);
  }

  private async findInConversation(conversationId: string, quizId: string): Promise<QuizRow> {
    const quiz = await this.quizzesRepository.findById(quizId);
    if (!quiz || quiz.conversation_id !== conversationId) throw new NotFoundException('Quiz not found');
    return quiz;
  }

  private async notifyChanged(conversationId: string, userId: string): Promise<void> {
    const otherIds = await this.conversationsRepository.listOtherMemberIds(conversationId, userId);
    this.realtimeBroadcaster.broadcastToUsers(otherIds, { type: 'quiz.changed', conversationId });
  }

  private async assertDirectMember(conversationId: string, userId: string): Promise<void> {
    if (!(await this.conversationsRepository.isMember(conversationId, userId))) {
      throw new ForbiddenException('Not a member of this conversation');
    }
    if (await this.conversationsRepository.isGroup(conversationId)) {
      throw new BadRequestException('Quizzes are only available in direct conversations');
    }
  }
}

export type { QuizResultRow };
