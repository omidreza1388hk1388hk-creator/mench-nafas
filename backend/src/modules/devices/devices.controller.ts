import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Put,
  Req,
  UseGuards,
} from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { DevicesRepository } from './devices.repository';
import { RegisterPushTokenDto } from './dto/register-push-token.dto';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { SessionsRepository } from '../sessions/sessions.repository';

/** The shape actually safe to return to a client — never push_token/push_provider/revoked_at, which are either sensitive or meaningless for a row this endpoint only ever returns while still active. */
interface DeviceSessionResponse {
  id: string;
  deviceName: string;
  platform: string;
  lastSeenAt: string | null;
  createdAt: string;
  /** True for the device that made this request, so the app can badge "this device". */
  isCurrent: boolean;
}

function toDeviceSessionResponse(currentDeviceId: string | undefined, device: {
  id: string;
  device_name: string;
  platform: string;
  last_seen_at: Date | null;
  created_at: Date;
}): DeviceSessionResponse {
  return {
    id: device.id,
    deviceName: device.device_name,
    platform: device.platform,
    lastSeenAt: device.last_seen_at ? device.last_seen_at.toISOString() : null,
    createdAt: device.created_at.toISOString(),
    isCurrent: device.id === currentDeviceId,
  };
}

/**
 * Phase 7: full device management, following up on this controller's
 * previous doc comment (master-prompt section 33). Both endpoints below
 * reuse DevicesRepository/SessionsRepository methods that already
 * existed — logout-all (AuthService) proved out the same
 * revoke-then-return-ok shape this just exposes per-device instead of
 * for every device at once.
 */
@UseGuards(JwtAuthGuard)
@Controller('devices')
export class DevicesController {
  constructor(
    private readonly devicesRepository: DevicesRepository,
    private readonly sessionsRepository: SessionsRepository,
  ) {}

  @Get()
  async listDevices(@Req() req: any): Promise<DeviceSessionResponse[]> {
    const devices = await this.devicesRepository.listActiveForUser(req.user.sub);
    return devices.map((d) => toDeviceSessionResponse(req.user.deviceId, d));
  }

  /**
   * Log out every device except the one making the request. Declared BEFORE ':deviceId' so "others" is never
   * parsed as a UUID. Returns how many devices were logged out.
   */
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Delete('others')
  @HttpCode(200)
  async revokeOtherDevices(@Req() req: any) {
    const keep: string = req.user.deviceId;
    const revoked = await this.devicesRepository.revokeAllExcept(req.user.sub, keep);
    await this.sessionsRepository.revokeAllExceptDevice(req.user.sub, keep);
    return { ok: true, revoked };
  }

  @Delete(':deviceId')
  async revokeDevice(@Req() req: any, @Param('deviceId', ParseUUIDPipe) deviceId: string) {
    // 404, not 403, for a device that isn't this user's — same
    // information-leak-avoidance rule as registerPushToken below.
    const device = await this.devicesRepository.findByIdForUser(deviceId, req.user.sub);
    if (!device) {
      throw new NotFoundException('Device not found');
    }

    await this.devicesRepository.revoke(deviceId, req.user.sub);
    await this.sessionsRepository.revokeAllForDevice(deviceId, req.user.sub);
    return { ok: true };
  }

  @Put(':deviceId/push-token')
  async registerPushToken(
    @Req() req: any,
    @Param('deviceId', ParseUUIDPipe) deviceId: string,
    @Body() dto: RegisterPushTokenDto,
  ) {
    // 404, not 403, for a device that isn't this user's (or doesn't
    // exist, or is revoked) — same information-leak-avoidance rule this
    // codebase already applies to conversations (see
    // ConversationsService.assertMember).
    const device = await this.devicesRepository.findByIdForUser(deviceId, req.user.sub);
    if (!device) {
      throw new NotFoundException('Device not found');
    }

    await this.devicesRepository.updatePushToken(deviceId, req.user.sub, dto.token, dto.provider);
    return { ok: true };
  }
}
