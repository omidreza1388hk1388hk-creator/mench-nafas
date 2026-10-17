import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Post, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { IsIn, IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';
import { AccountService } from './account.service';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

class BlockDto {
  @IsUUID() userId!: string;
}
class DeleteAccountDto {
  @IsString() confirm!: string;
}
class FeedbackDto {
  @IsOptional() @IsString() @MaxLength(32) category?: string;
  @IsString() @MaxLength(4000) message!: string;
  @IsOptional() @IsString() @MaxLength(40) appVersion?: string;
  @IsOptional() @IsString() @MaxLength(200) deviceInfo?: string;
  @IsOptional() @IsString() @MaxLength(60000) logs?: string;
}
class EventDto {
  @IsString() @IsIn(['app_open', 'onboarding_completed', 'onboarding_skipped', 'invite_shared', 'qr_scanned', 'message_sent', 'call_started'])
  name!: string;
}

@UseGuards(JwtAuthGuard)
@Controller()
export class AccountController {
  constructor(private readonly service: AccountService) {}

  @Get('account/invite')
  myInvite(@Req() req: any) {
    return this.service.myInvite(req.user.sub);
  }

  @Get('account/invite/:code')
  resolveInvite(@Param('code') code: string) {
    return this.service.resolveInvite(code);
  }

  @Get('account/by-username/:username')
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  byUsername(@Param('username') username: string) {
    return this.service.byUsername(username);
  }

  @Get('account/blocks')
  listBlocked(@Req() req: any) {
    return this.service.listBlocked(req.user.sub);
  }

  @Post('account/blocks')
  @HttpCode(204)
  async block(@Req() req: any, @Body() dto: BlockDto) {
    await this.service.block(req.user.sub, dto.userId);
  }

  @Delete('account/blocks/:userId')
  @HttpCode(204)
  async unblock(@Req() req: any, @Param('userId', ParseUUIDPipe) userId: string) {
    await this.service.unblock(req.user.sub, userId);
  }

  @Get('account/export')
  @Throttle({ default: { limit: 3, ttl: 3_600_000 } })
  exportData(@Req() req: any) {
    return this.service.exportData(req.user.sub);
  }

  @Post('account/delete')
  @HttpCode(204)
  @Throttle({ default: { limit: 3, ttl: 3_600_000 } })
  async deleteAccount(@Req() req: any, @Body() dto: DeleteAccountDto) {
    await this.service.deleteAccount(req.user.sub, dto.confirm);
  }

  @Post('feedback')
  @HttpCode(204)
  @Throttle({ default: { limit: 5, ttl: 3_600_000 } })
  async feedback(@Req() req: any, @Body() dto: FeedbackDto) {
    await this.service.feedback(req.user.sub, dto);
  }

  @Get('config')
  config(@Req() req: any) {
    return this.service.remoteConfig(req.user.sub);
  }

  @Post('analytics/event')
  @HttpCode(204)
  @Throttle({ default: { limit: 60, ttl: 60_000 } })
  async event(@Body() dto: EventDto) {
    await this.service.countEvent(dto.name);
  }
}
