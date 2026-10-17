import { ForbiddenException } from '@nestjs/common';
import { SafetyService } from './safety.service';

type Q = (sql: string, params?: unknown[]) => Promise<{ rows: any[]; rowCount?: number }>;

function makePool(handlers: Array<[RegExp, (params?: unknown[]) => any[]]>) {
  const calls: Array<{ sql: string; params?: unknown[] }> = [];
  const query: Q = async (sql, params) => {
    calls.push({ sql, params });
    for (const [re, fn] of handlers) if (re.test(sql)) return { rows: fn(params) };
    return { rows: [] };
  };
  return { pool: { query } as any, calls };
}

const FLOOD = { key: 'flood', enabled: true, threshold: 3, window_seconds: 60, action_hours: 1 };
const REPEAT = {
  key: 'repeat_message',
  enabled: true,
  threshold: 3,
  window_seconds: 300,
  action_hours: 2,
};

describe('SafetyService', () => {
  it('lets a normal sender through', async () => {
    const { pool } = makePool([[/FROM moderation_rules/, () => [FLOOD, REPEAT]]]);
    const svc = new SafetyService(pool);
    await expect(svc.assertCanSend('u1', 'hello')).resolves.toBeUndefined();
  });

  it('blocks a user with an active restriction', async () => {
    const { pool } = makePool([
      [/FROM user_restrictions/, () => [{ kind: 'mute', expires_at: null }]],
    ]);
    const svc = new SafetyService(pool);
    await expect(svc.assertCanSend('u1', 'hi')).rejects.toThrow(ForbiddenException);
  });

  it('auto-mutes on flooding and writes an explainable audit entry', async () => {
    const { pool, calls } = makePool([
      [/FROM moderation_rules/, () => [FLOOD]],
      [/INSERT INTO user_restrictions/, () => [{ id: 'r1' }]],
    ]);
    const svc = new SafetyService(pool);
    for (let i = 0; i < 3; i++) await svc.assertCanSend('u1', `m${i}`);
    await expect(svc.assertCanSend('u1', 'm4')).rejects.toThrow(ForbiddenException);
    const audit = calls.find((c) => /INSERT INTO moderation_audit/.test(c.sql));
    expect(audit).toBeDefined();
    expect(audit!.params![0]).toBe('rule:flood');
    expect(audit!.params![1]).toBe('auto_mute');
  });

  it('auto-mutes on the same text repeated', async () => {
    const { pool } = makePool([
      [/FROM moderation_rules/, () => [REPEAT]],
      [/INSERT INTO user_restrictions/, () => [{ id: 'r2' }]],
    ]);
    const svc = new SafetyService(pool);
    await svc.assertCanSend('u2', 'buy now http://x');
    await svc.assertCanSend('u2', 'buy now http://x');
    await expect(svc.assertCanSend('u2', 'Buy now http://x')).rejects.toThrow(ForbiddenException);
  });

  it('rejects an unknown report category', async () => {
    const { pool } = makePool([]);
    const svc = new SafetyService(pool);
    await expect(
      svc.createReport('u1', { targetUserId: 'u2', category: 'nonsense' }),
    ).rejects.toThrow();
  });
});
