import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { WsAdapter } from '@nestjs/platform-ws';
import { AppModule } from './app.module';
import { HttpExceptionFilter } from './common/filters/http-exception.filter';

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
