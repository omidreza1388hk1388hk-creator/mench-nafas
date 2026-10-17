import { EventEmitter } from 'events';
import { requestLogger, securityHeaders } from './request-logger';

function fakeRes(status = 200) {
  const res: any = new EventEmitter();
  res.statusCode = status;
  res.headers = {} as Record<string, string>;
  res.setHeader = (k: string, v: string) => {
    res.headers[k] = v;
  };
  return res;
}
function fakeReq(url: string, headers: Record<string, string> = {}) {
  return {
    method: 'GET',
    originalUrl: url,
    url,
    header: (n: string) => headers[n.toLowerCase()],
  } as any;
}

describe('requestLogger', () => {
  it('logs path without the query string and sets X-Request-Id', () => {
    const lines: string[] = [];
    const res = fakeRes(200);
    const next = jest.fn();
    requestLogger((l) => lines.push(l))(fakeReq('/api/v1/auth/otp/verify?code=12345'), res, next);
    res.emit('finish');
    expect(next).toHaveBeenCalled();
    expect(res.headers['X-Request-Id']).toBeTruthy();
    const entry = JSON.parse(lines[0]);
    expect(entry.path).toBe('/api/v1/auth/otp/verify');
    expect(lines[0]).not.toContain('12345');
    expect(entry.level).toBe('info');
    expect(entry.status).toBe(200);
  });

  it('reuses a sane incoming request id and rejects a suspicious one', () => {
    const good = fakeRes();
    requestLogger(() => undefined)(fakeReq('/x', { 'x-request-id': 'abc-12345678' }), good, jest.fn());
    expect(good.headers['X-Request-Id']).toBe('abc-12345678');

    const bad = fakeRes();
    requestLogger(() => undefined)(fakeReq('/x', { 'x-request-id': 'a b\nc' }), bad, jest.fn());
    expect(bad.headers['X-Request-Id']).not.toContain('\n');
  });

  it('uses warn for 4xx and error for 5xx', () => {
    const lines: string[] = [];
    const r4 = fakeRes(404);
    const r5 = fakeRes(503);
    requestLogger((l) => lines.push(l))(fakeReq('/a'), r4, jest.fn());
    requestLogger((l) => lines.push(l))(fakeReq('/b'), r5, jest.fn());
    r4.emit('finish');
    r5.emit('finish');
    expect(JSON.parse(lines[0]).level).toBe('warn');
    expect(JSON.parse(lines[1]).level).toBe('error');
  });
});

describe('securityHeaders', () => {
  it('sets baseline hardening headers', () => {
    const res = fakeRes();
    const next = jest.fn();
    securityHeaders()({} as any, res, next);
    expect(res.headers['X-Content-Type-Options']).toBe('nosniff');
    expect(res.headers['X-Frame-Options']).toBe('DENY');
    expect(next).toHaveBeenCalled();
  });
});
