import type { NextFunction, Request, Response } from 'express';

/**
 * Phase 4B: tiny Prometheus-format metrics, no dependency. Counts finished requests by status class and
 * sums latency. Exposed at GET /metrics ONLY when METRICS_TOKEN is set (send it as `Authorization: Bearer <token>`).
 */
const counts: Record<string, number> = {};
let latencySumMs = 0;
let latencyCount = 0;
const startedAt = Date.now();

export function metricsMiddleware() {
  return (_req: Request, res: Response, next: NextFunction): void => {
    const started = process.hrtime.bigint();
    res.on('finish', () => {
      const cls = `${Math.floor(res.statusCode / 100)}xx`;
      counts[cls] = (counts[cls] ?? 0) + 1;
      latencySumMs += Number(process.hrtime.bigint() - started) / 1e6;
      latencyCount += 1;
    });
    next();
  };
}

export function renderMetrics(): string {
  const mem = process.memoryUsage();
  const lines = [
    '# HELP mench_http_requests_total Finished HTTP requests by status class.',
    '# TYPE mench_http_requests_total counter',
    ...Object.entries(counts).map(([cls, n]) => `mench_http_requests_total{status="${cls}"} ${n}`),
    '# HELP mench_http_request_duration_ms_sum Total request latency in milliseconds.',
    '# TYPE mench_http_request_duration_ms_sum counter',
    `mench_http_request_duration_ms_sum ${latencySumMs.toFixed(1)}`,
    `mench_http_request_duration_ms_count ${latencyCount}`,
    '# TYPE mench_process_uptime_seconds gauge',
    `mench_process_uptime_seconds ${Math.floor((Date.now() - startedAt) / 1000)}`,
    '# TYPE mench_process_resident_memory_bytes gauge',
    `mench_process_resident_memory_bytes ${mem.rss}`,
    '# TYPE mench_process_heap_used_bytes gauge',
    `mench_process_heap_used_bytes ${mem.heapUsed}`,
  ];
  return lines.join('\n') + '\n';
}
