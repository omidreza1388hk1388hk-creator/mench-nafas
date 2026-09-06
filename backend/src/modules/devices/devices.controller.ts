import { Body, Controller, NotFoundException, Param, ParseUUIDPipe, Put, Req, UseGuards } from '@nestjs/common';
import { DevicesRepository } from './devices.repository';
import { RegisterPushTokenDto } from './dto/register-push-token.dto';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

/**
 * Phase 6's only device endpoint. Full device management (list active
 * devices, "logout this device", last-activity display — master-prompt
 * section 33) is not built yet and is explicitly out of scope for this
 * phase; this controller exists solely so the Android app has somewhere
 * to register its FCM token after login and on token refresh.
 */
@UseGuards(JwtAuthGuard)
@Controller('devices')
export class DevicesController {
  constructor(private readonly devicesRepository: DevicesRepository) {}

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
