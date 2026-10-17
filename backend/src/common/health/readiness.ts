/**
 * Readiness probe (Phase 4B): "can this instance serve traffic right now?".
 * Liveness (`GET /health`) only says the process is up; readiness also proves the
 * database and Redis answer, so a load balancer can stop routing to a sick instance.
 * Pure function over minimal interfaces so it is unit-testable without a database.
 */
export interface Pingable {
  ping(): Promise<unknown>;
}
export interface Queryable {
  query(text: string): Promise<unknown>;
}

export interface ReadinessResult {
  ok: boolean;
  checks: { database: 'up' | 'down'; redis: 'up' | 'down' };
  serverTime: string;
}

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  let timer: NodeJS.Timeout | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error('timeout')), ms);
  });
  return Promise.race([promise, timeout]).finally(() => {
    if (timer) clearTimeout(timer);
  });
}

export async function checkReadiness(
  pool: Queryable,
  redis: Pingable,
  timeoutMs = 2000,
): Promise<ReadinessResult> {
  const [db, cache] = await Promise.all([
    withTimeout(pool.query('SELECT 1'), timeoutMs).then(
      () => 'up' as const,
      () => 'down' as const,
    ),
    withTimeout(redis.ping(), timeoutMs).then(
      () => 'up' as const,
      () => 'down' as const,
    ),
  ]);
  return {
    ok: db === 'up' && cache === 'up',
    checks: { database: db, redis: cache },
    serverTime: new Date().toISOString(),
  };
}
