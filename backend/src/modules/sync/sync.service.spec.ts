import { BadRequestException } from '@nestjs/common';
import { SyncService } from './sync.service';

describe('SyncService', () => {
  const query = jest.fn();
  const service = new SyncService({ query } as never);
  beforeEach(() => query.mockReset());

  it('rejects keys outside draft:/setting:', async () => {
    await expect(service.put('u', 'secret:x', 'v')).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.get('u', '../etc')).rejects.toBeInstanceOf(BadRequestException);
    expect(query).not.toHaveBeenCalled();
  });

  it('rejects values over 8 KB', async () => {
    await expect(service.put('u', 'draft:abc', 'x'.repeat(9000))).rejects.toBeInstanceOf(BadRequestException);
  });

  it('upserts and returns the entry', async () => {
    query.mockResolvedValue({ rows: [{ key: 'draft:abc', value: 'hi', updated_at: new Date('2026-10-06T00:00:00Z') }] });
    await expect(service.put('u', 'draft:abc', 'hi')).resolves.toEqual({ key: 'draft:abc', value: 'hi', updatedAt: '2026-10-06T00:00:00.000Z' });
    expect(query.mock.calls[0][1]).toEqual(['u', 'draft:abc', 'hi']);
  });

  it('rejects an invalid since date', async () => {
    await expect(service.changedSince('u', 'not-a-date')).rejects.toBeInstanceOf(BadRequestException);
  });
});
