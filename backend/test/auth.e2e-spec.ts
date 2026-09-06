import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication, ValidationPipe } from '@nestjs/common';
import request from 'supertest';
import { AppModule } from '../src/app.module';

/**
 * End-to-end smoke test for the OTP auth flow. Requires a real Postgres +
 * Redis reachable via DATABASE_URL / REDIS_URL (see docs/ENVIRONMENT.md) —
 * this intentionally exercises the real database, not a mock, since auth
 * correctness is exactly the kind of thing that should not be validated
 * against a fake.
 *
 * Run with: npm run test:e2e  (after `npm run migrate`)
 */
describe('Auth flow (e2e)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleRef: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleRef.createNestApplication();
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
    app.setGlobalPrefix('api/v1');
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  it('rejects OTP verification with a wrong code', async () => {
    const phone = `+1415${Math.floor(1000000 + Math.random() * 8999999)}`;

    const requestRes = await request(app.getHttpServer())
      .post('/api/v1/auth/otp/request')
      .send({ phone })
      .expect(200);

    await request(app.getHttpServer())
      .post('/api/v1/auth/otp/verify')
      .send({
        phone,
        code: '00000',
        challengeId: requestRes.body.challengeId,
        deviceName: 'e2e-test-device',
      })
      .expect(401);
  });
});
