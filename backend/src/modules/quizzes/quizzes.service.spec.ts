import { BadRequestException, ConflictException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { QuizzesService } from './quizzes.service';

const userA = 'user-a';
const userB = 'user-b';
const questions = [
  { q: 'Colour?', options: ['Red', 'Blue'], correct: 1 },
  { q: 'Pet?', options: ['Cat', 'Dog', 'Fish'], correct: 2 },
];
const quiz = { id: 'q-1', conversation_id: 'conv-1', author_id: userA, title: 'Me', questions, created_at: new Date() };

describe('QuizzesService', () => {
  let repo: Record<string, jest.Mock>;
  let conversations: Record<string, jest.Mock>;
  let broadcaster: Record<string, jest.Mock>;
  let service: QuizzesService;

  beforeEach(() => {
    repo = {
      list: jest.fn().mockResolvedValue([quiz]), results: jest.fn().mockResolvedValue([]), countByAuthor: jest.fn().mockResolvedValue(0),
      create: jest.fn().mockResolvedValue(quiz), findById: jest.fn().mockResolvedValue(quiz), findResult: jest.fn().mockResolvedValue(null),
      saveResult: jest.fn().mockResolvedValue(true), delete: jest.fn(),
    };
    conversations = { isMember: jest.fn().mockResolvedValue(true), isGroup: jest.fn().mockResolvedValue(false), listOtherMemberIds: jest.fn().mockResolvedValue([userB]) };
    broadcaster = { broadcastToUsers: jest.fn() };
    service = new QuizzesService(repo as any, conversations as any, broadcaster as any);
  });

  it('hides the right answers from the taker until they have answered, and always shows them to the author', async () => {
    expect((await service.get('conv-1', 'q-1', userB)).questions.map((q) => q.correct)).toEqual([null, null]);
    expect((await service.get('conv-1', 'q-1', userA)).questions.map((q) => q.correct)).toEqual([1, 2]);
    repo.findResult.mockResolvedValue({ score: 1 });
    expect((await service.get('conv-1', 'q-1', userB)).questions.map((q) => q.correct)).toEqual([1, 2]);
  });

  it('scores an answer, stores it once and tells the author', async () => {
    await expect(service.answer('conv-1', 'q-1', userB, [1, 0])).resolves.toEqual({ score: 1, total: 2, correct: [1, 2] });
    expect(repo.saveResult).toHaveBeenCalledWith('q-1', userB, [1, 0], 1, 2);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith([userA], { type: 'quiz.changed', conversationId: 'conv-1' });
    repo.saveResult.mockResolvedValue(false);
    await expect(service.answer('conv-1', 'q-1', userB, [1, 2])).rejects.toBeInstanceOf(ConflictException);
  });

  it('rejects answering your own quiz and out-of-range answers', async () => {
    await expect(service.answer('conv-1', 'q-1', userA, [1, 2])).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.answer('conv-1', 'q-1', userB, [1])).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.answer('conv-1', 'q-1', userB, [1, 9])).rejects.toBeInstanceOf(BadRequestException);
  });

  it('lists summaries with the right side of the result', async () => {
    repo.results.mockResolvedValue([{ quiz_id: 'q-1', user_id: userB, score: 2, total: 2 }]);
    expect((await service.list('conv-1', userA))[0]).toMatchObject({ isMine: true, partnerScore: 2, partnerTotal: 2, myScore: null });
    expect((await service.list('conv-1', userB))[0]).toMatchObject({ isMine: false, myScore: 2, myTotal: 2, partnerScore: null });
  });

  it('creates a validated quiz, enforces the cap, and only the author can delete', async () => {
    await service.create('conv-1', userA, { title: ' Me ', questions });
    expect(repo.create).toHaveBeenCalledWith('conv-1', userA, 'Me', questions);
    await expect(service.create('conv-1', userA, { title: 'x', questions: [] })).rejects.toBeInstanceOf(BadRequestException);
    repo.countByAuthor.mockResolvedValue(10);
    await expect(service.create('conv-1', userA, { title: 'x', questions })).rejects.toBeInstanceOf(BadRequestException);

    await expect(service.remove('conv-1', 'q-1', userB)).rejects.toBeInstanceOf(ForbiddenException);
    repo.findById.mockResolvedValue({ ...quiz, conversation_id: 'other' });
    await expect(service.remove('conv-1', 'q-1', userA)).rejects.toBeInstanceOf(NotFoundException);
  });

  it('is unavailable in groups and to non-members', async () => {
    conversations.isGroup.mockResolvedValue(true);
    await expect(service.list('conv-1', userA)).rejects.toBeInstanceOf(BadRequestException);
    conversations.isMember.mockResolvedValue(false);
    await expect(service.list('conv-1', userA)).rejects.toBeInstanceOf(ForbiddenException);
  });
});
