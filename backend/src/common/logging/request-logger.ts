import { randomUUID } from 'crypto';
import type { NextFunction, Request, Response } from 'express';

/**
 * Structured request logging + baseline security headers (Phase 4B / 4C).
 *
 * - One JSON line per finished request: easy to ship to Loki/ELK/Railway logs.
 * - Only the path is logged, never the query string, headers, or body, so OTP codes,
 *   tokens and message content can never leak through access logs.
 * - Every response carries an X-Request-Id (reused from the caller if it is sane).
 */
const SAFE_ID = /^[A-Za-z0-9_.-]{8,64}$/;

export function requestLogger(
  write: (line: string) => void = (line) => process.stdout.write(line + '\n'),
) {
  return (req: Request, res: Response, next: NextFunction): void => {
    const incoming = req.header('x-request-id');
    const requestId = incoming && SAFE_ID.test(incoming) ? incoming : randomUUID();
    const started = process.hrtime.bigint();
    res.setHeader('X-Request-Id', requestId);

    res.on('finish', () => {
      const ms = Number(process.hrtime.bigint() - started) / 1e6;
      const path = (req.originalUrl || req.url || '').split('?')[0];
      write(
        JSON.stringify({
          ts: new Date().toISOString(),
          level: res.statusCode >= 500 ? 'error' : res.statusCode >= 400 ? 'warn' : 'info',
          msg: 'http_request',
          requestId,
          method: req.method,
          path,
          status: res.statusCode,
          durationMs: Math.round(ms * 10) / 10,
        }),
      );
    });
    next();
  };
}

export function securityHeaders() {
  return (_req: Request, res: Response, next: NextFunction): void => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Strict-Transport-Security', 'max-age=15552000; includeSubDomains');
    res.setHeader('Cross-Origin-Resource-Policy', 'same-site');
    next();
  };
}
