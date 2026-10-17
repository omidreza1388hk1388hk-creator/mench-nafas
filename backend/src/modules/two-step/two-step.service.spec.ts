import { BadRequestException, HttpException, UnauthorizedException } from '@nestjs/common';
import { TwoStepService } from './two-step.service';

// Cheap deterministic stand-in so the spec does not depend on the native argon2 binding.
jest.mock('argon2', () => ({
  hash: async (p: string) => `h:${p}`,
  verify: async (h: string, p: string) => h === `h:${p}`,
}));

function build(row: any = null) {
  const state = { row, ticket: null as any, failures: 0 };
  const repo = {
    get: jest.fn(async () => state.row),
    upsert: jest.fn(async (_u: string, hash: string, hint: string | null) => {
      state.row = { user_id: 'u1', password_hash: hash, hint, failed_attempts: 0, locked_until: null };
    }),
    remove: jest.fn(async () => {
      state.row = null;
    }),
    recordFailure: jest.fn(async () => {
      state.failures += 1;
    }),
    recordSuccess: jest.fn(),
    createTicket: jest.fn(async (u: string, d: string, e: Date) => {
      state.ticket = { id: 't1', user_id: u, device_name: d, attempts: 0, expires_at: e };
      return 't1';
    }),
    getTicket: jest.fn(async () => state.ticket),
    bumpTicket: jest.fn(),
    deleteTicket: jest.fn(),
  };
  return { service: new TwoStepService(repo as never), repo, state };
}

describe('TwoStepService', () => {
  it('stores only a hash and reports status', async () => {
    const { service, state } = build();
    await service.set('u1', 'correct horse', 'a hint', undefined);
    expect(state.row.password_hash).not.toBe('correct horse');
    await expect(service.status('u1')).resolves.toEqual({ enabled: true, hint: 'a hint' });
  });

  it('rejects weak passwords and hints that contain the password', async () => {
    const { service } = build();
    await expect(service.set('u1', '123', undefined, undefined)).rejects.toBeInstanceOf(BadRequestException);
    await expect(service.set('u1', 'secret99', 'my secret99', undefined)).rejects.toBeInstanceOf(BadRequestException);
  });

  it('changing the password requires the current one', async () => {
    const { service } = build({ user_id: 'u1', password_hash: 'h:old-pass', hint: null, failed_attempts: 0, locked_until: null });
    await expect(service.set('u1', 'new-pass-1', undefined, 'wrong')).rejects.toBeInstanceOf(UnauthorizedException);
    await expect(service.set('u1', 'new-pass-1', undefined, 'old-pass')).resolves.toBeUndefined();
  });

  it('a correct ticket + password returns the user and burns the ticket', async () => {
    const { service, repo } = build({ user_id: 'u1', password_hash: 'h:pw-1234', hint: null, failed_attempts: 0, locked_until: null });
    const challenge = await service.startChallenge('u1', 'Pixel 7');
    expect(challenge).toMatchObject({ twoStepRequired: true, ticket: 't1' });
    await expect(service.verifyTicket('t1', 'pw-1234')).resolves.toEqual({ userId: 'u1', deviceName: 'Pixel 7' });
    expect(repo.deleteTicket).toHaveBeenCalledWith('t1');
  });

  it('a wrong password counts against both the account and the ticket', async () => {
    const { service, repo } = build({ user_id: 'u1', password_hash: 'h:pw-1234', hint: null, failed_attempts: 0, locked_until: null });
    await service.startChallenge('u1', 'Pixel 7');
    await expect(service.verifyTicket('t1', 'nope')).rejects.toBeInstanceOf(UnauthorizedException);
    expect(repo.recordFailure).toHaveBeenCalled();
    expect(repo.bumpTicket).toHaveBeenCalledWith('t1');
  });

  it('a locked account answers 429 even for the right password', async () => {
    const future = new Date(Date.now() + 60_000);
    const { service } = build({ user_id: 'u1', password_hash: 'h:pw-1234', hint: null, failed_attempts: 5, locked_until: future });
    await service.startChallenge('u1', 'Pixel 7');
    await expect(service.verifyTicket('t1', 'pw-1234')).rejects.toBeInstanceOf(HttpException);
  });

  it('an expired ticket is refused', async () => {
    const { service, state } = build({ user_id: 'u1', password_hash: 'h:pw-1234', hint: null, failed_attempts: 0, locked_until: null });
    await service.startChallenge('u1', 'Pixel 7');
    state.ticket.expires_at = new Date(Date.now() - 1000);
    await expect(service.verifyTicket('t1', 'pw-1234')).rejects.toBeInstanceOf(UnauthorizedException);
  });

  it('disable needs the password and removes the row', async () => {
    const { service, state } = build({ user_id: 'u1', password_hash: 'h:pw-1234', hint: null, failed_attempts: 0, locked_until: null });
    await expect(service.disable('u1', 'wrong')).rejects.toBeInstanceOf(UnauthorizedException);
    await service.disable('u1', 'pw-1234');
    expect(state.row).toBeNull();
  });
});
