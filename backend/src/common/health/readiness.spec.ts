import { checkReadiness } from './readiness';

describe('checkReadiness', () => {
  const okPool = { query: jest.fn().mockResolvedValue({}) };
  const okRedis = { ping: jest.fn().mockResolvedValue('PONG') };

  it('is ok when database and redis answer', async () => {
    const r = await checkReadiness(okPool, okRedis);
    expect(r.ok).toBe(true);
    expect(r.checks).toEqual({ database: 'up', redis: 'up' });
  });

  it('is not ok when the database fails', async () => {
    const r = await checkReadiness({ query: jest.fn().mockRejectedValue(new Error('down')) }, okRedis);
    expect(r.ok).toBe(false);
    expect(r.checks).toEqual({ database: 'down', redis: 'up' });
  });

  it('is not ok when redis fails', async () => {
    const r = await checkReadiness(okPool, { ping: jest.fn().mockRejectedValue(new Error('down')) });
    expect(r.ok).toBe(false);
    expect(r.checks).toEqual({ database: 'up', redis: 'down' });
  });

  it('treats a hung dependency as down after the timeout', async () => {
    const hung = { ping: () => new Promise<never>(() => undefined) };
    const r = await checkReadiness(okPool, hung, 20);
    expect(r.checks.redis).toBe('down');
    expect(r.ok).toBe(false);
  });
});
