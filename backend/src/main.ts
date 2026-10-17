import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { WsAdapter } from '@nestjs/platform-ws';
import { AppModule } from './app.module';
import { HttpExceptionFilter } from './common/filters/http-exception.filter';
import { requestLogger, securityHeaders } from './common/logging/request-logger';
import { metricsMiddleware, renderMetrics } from './common/metrics/metrics';
import { checkReadiness } from './common/health/readiness';
import { PG_POOL } from './database/database.module';
import { REDIS_CLIENT } from './redis/redis.module';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, {
    logger: ['error', 'warn', 'log'],
  });

  // Global input validation — every DTO is validated before it reaches
  // a controller. Unknown properties are rejected (forbidNonWhitelisted)
  // so clients can't smuggle extra fields into the payload.
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  app.useGlobalFilters(new HttpExceptionFilter());

  // Phase 4B/4C: structured access log (no query strings / bodies) + baseline security headers.
  app.use(securityHeaders());
  app.use(requestLogger());
  app.use(metricsMiddleware());
  // Phase 4B: Prometheus metrics, off unless METRICS_TOKEN is configured.
  app.getHttpAdapter().get('/metrics', (req: any, res: any) => {
    const token = process.env.METRICS_TOKEN;
    if (!token || req.headers.authorization !== `Bearer ${token}`) {
      res.status(404).json({ message: 'Not found' });
      return;
    }
    res.type('text/plain; version=0.0.4').send(renderMetrics());
  });

  // Conventional unauthenticated health endpoint for Railway/uptime checks.
  // Keep it outside /api/v1 so platform health checks remain stable even if
  // the API version changes.
  app.getHttpAdapter().get('/health', (_req, res) => {
    res.status(200).json({ ok: true, serverTime: new Date().toISOString() });
  });
  // Readiness: 200 only when Postgres and Redis both answer (503 otherwise) so a load
  // balancer can drain a sick instance. Liveness above stays dependency-free.
  app.getHttpAdapter().get('/health/ready', async (_req, res) => {
    const result = await checkReadiness(
      app.get(PG_POOL, { strict: false }),
      app.get(REDIS_CLIENT, { strict: false }),
    );
    res.status(result.ok ? 200 : 503).json(result);
  });
  app.setGlobalPrefix('api/v1');

  // Raw 'ws' instead of the default socket.io adapter: ChatGateway's wire
  // format is plain JSON over a standard WebSocket, so the Android client
  // can connect with OkHttp's built-in WebSocket support directly — no
  // socket.io protocol/client library needed on either side. Mounted at
  // /ws (NOT /api/v1/ws — setGlobalPrefix only affects the HTTP router).
  app.useWebSocketAdapter(new WsAdapter(app));

  const port = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;
  await app.listen(port);
  // eslint-disable-next-line no-console
  console.log(`MENCH backend listening on :${port} (api/v1, ws at /ws)`);
}

bootstrap();
