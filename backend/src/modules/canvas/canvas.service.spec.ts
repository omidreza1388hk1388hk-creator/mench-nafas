import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { CanvasService } from './canvas.service';
import { MAX_STROKES_PER_CANVAS } from './strokes';

const userA = 'user-a';
const userB = 'user-b';
const stroke = { color: '#112233', width: 4, points: [0.1, 0.1, 0.2, 0.2] };

describe('CanvasService', () => {
  let canvas: Record<string, jest.Mock>;
  let conversations: Record<string, jest.Mock>;
  let broadcaster: Record<string, jest.Mock>;
  let service: CanvasService;

  beforeEach(() => {
    canvas = { get: jest.fn().mockResolvedValue([]), append: jest.fn().mockResolvedValue(1), clear: jest.fn() };
    conversations = {
      isMember: jest.fn().mockResolvedValue(true),
      isGroup: jest.fn().mockResolvedValue(false),
      listOtherMemberIds: jest.fn().mockResolvedValue([userB]),
    };
    broadcaster = { broadcastToUsers: jest.fn() };
    service = new CanvasService(canvas as any, conversations as any, broadcaster as any);
  });

  it('appends sanitized strokes stamped with the author and tells the partner', async () => {
    await expect(service.append('conv-1', userA, [{ ...stroke, evil: 1 }])).resolves.toEqual({ count: 1 });
    expect(canvas.append).toHaveBeenCalledWith('conv-1', [{ ...stroke, by: userA }]);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith([userB], { type: 'canvas.changed', conversationId: 'conv-1' });
  });

  it('rejects invalid strokes without touching the database', async () => {
    await expect(service.append('conv-1', userA, [{ ...stroke, color: 'blue' }])).rejects.toBeInstanceOf(BadRequestException);
    expect(canvas.append).not.toHaveBeenCalled();
  });

  it('rejects when the canvas would overflow', async () => {
    canvas.get.mockResolvedValue(Array(MAX_STROKES_PER_CANVAS).fill(stroke));
    await expect(service.append('conv-1', userA, [stroke])).rejects.toBeInstanceOf(BadRequestException);
  });

  it('clears and notifies; is unavailable in groups and to non-members', async () => {
    await service.clear('conv-1', userA);
    expect(canvas.clear).toHaveBeenCalledWith('conv-1');
    expect(broadcaster.broadcastToUsers).toHaveBeenCalled();

    conversations.isGroup.mockResolvedValue(true);
    await expect(service.get('conv-1', userA)).rejects.toBeInstanceOf(BadRequestException);
    conversations.isMember.mockResolvedValue(false);
    await expect(service.get('conv-1', userA)).rejects.toBeInstanceOf(ForbiddenException);
  });
});
