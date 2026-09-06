import { Controller, Get, UseGuards } from '@nestjs/common';
import { DiagnosticsService } from './diagnostics.service';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

@Controller('diagnostics')
export class DiagnosticsController {
  constructor(private readonly diagnosticsService: DiagnosticsService) {}

  /**
   * Unauthenticated on purpose: a lightweight liveness probe (process is
   * up, can respond at all) for load balancers / uptime monitors, which
   * never carry a user's bearer token. Never returns anything beyond
   * "alive" — no dependency status here (see /diagnostics/full for that).
   */
  @Get('ping')
  ping() {
    return { ok: true, serverTime: new Date().toISOString() };
  }

  /**
   * Authenticated: this is what the Android app's "Run Diagnostics" /
   * Connection Health screen calls. Requiring a valid session here is
   * itself part of what it reports on (an expired/invalid token surfaces
   * as a 401 the client's DiagnosticsRepository turns into the
   * "Authentication" check's own fail state, not a request-level crash —
   * see RunDiagnosticsUseCase on the Android side).
   */
  @UseGuards(JwtAuthGuard)
  @Get('full')
  full() {
    return this.diagnosticsService.run();
  }
}
