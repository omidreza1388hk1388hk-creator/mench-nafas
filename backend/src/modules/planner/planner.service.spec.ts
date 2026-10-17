import { BadRequestException } from '@nestjs/common';
import { PlannerService } from './planner.service';

describe('PlannerService', () => {
  const query = jest.fn();
  const service = new PlannerService({ query } as never);

  beforeEach(() => query.mockReset());

  it('cleanParticipants trims, de-duplicates and caps names', () => {
    const many = Array.from({ length: 30 }, (_, i) => `person-${i}`);
    expect(service.cleanParticipants(['  Sara ', 'Sara', '', 'Ali'])).toEqual(['Sara', 'Ali']);
    expect(service.cleanParticipants(many)).toHaveLength(20);
    expect(service.cleanParticipants(['x'.repeat(100)])[0]).toHaveLength(60);
    expect(service.cleanParticipants(undefined)).toEqual([]);
  });

  it('stores participants and a cleaned checklist on create', async () => {
    query.mockResolvedValue({
      rows: [{ id: '1', kind: 'event', title: 'Dinner', participants: ['Sara'], checklist: [] }],
    });
    const dto = await service.create('user-1', {
      kind: 'event',
      title: 'Dinner',
      dueAt: new Date(Date.now() + 3600_000).toISOString(),
      participants: [' Sara ', 'Sara'],
      checklist: [
        { text: 'Book table', done: false },
        { text: '', done: true },
      ],
    });
    const params = query.mock.calls[0][1];
    expect(JSON.parse(params[11])).toEqual(['Sara']);
    expect(JSON.parse(params[8])).toEqual([{ text: 'Book table', done: false }]);
    expect(dto.participants).toEqual(['Sara']);
  });

  it('rejects an event without a date', async () => {
    await expect(
      service.create('user-1', { kind: 'event', title: 'No date' }),
    ).rejects.toBeInstanceOf(BadRequestException);
    expect(query).not.toHaveBeenCalled();
  });
});
