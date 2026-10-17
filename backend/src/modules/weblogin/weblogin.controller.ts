import {
  Body,
  Controller,
  Get,
  HttpCode,
  Headers,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { IsBoolean, IsString, MaxLength, MinLength } from 'class-validator';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { WebLoginService } from './weblogin.service';

class ClaimDto {
  @IsString() @MinLength(20) @MaxLength(100) secret!: string;
}
class ApproveDto {
  @IsString() @MinLength(10) @MaxLength(60) nonce!: string;
  @IsBoolean() approve!: boolean;
}

/** Public half: used by the Web/Desktop page that wants to sign in. */
@Controller('web-login')
export class WebLoginPublicController {
  constructor(private readonly service: WebLoginService) {}

  @Post('start')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  start(@Headers('user-agent') ua?: string) {
    return this.service.start(ua);
  }

  @Post(':id/claim')
  @HttpCode(200)
  @Throttle({ default: { limit: 120, ttl: 60_000 } })
  claim(@Param('id', ParseUUIDPipe) id: string, @Body() dto: ClaimDto) {
    return this.service.claim(id, dto.secret);
  }
}

/** Authenticated half: the already-signed-in phone shows the requesting device, then approves or rejects. */
@UseGuards(JwtAuthGuard)
@Controller('web-login')
export class WebLoginPhoneController {
  constructor(private readonly service: WebLoginService) {}

  @Get(':id')
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  describe(@Param('id', ParseUUIDPipe) id: string, @Query('nonce') nonce: string) {
    return this.service.describe(id, nonce ?? '');
  }

  @Post(':id/decision')
  @HttpCode(204)
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  async decide(@Req() req: any, @Param('id', ParseUUIDPipe) id: string, @Body() dto: ApproveDto) {
    await this.service.approve(id, dto.nonce, req.user.sub, dto.approve);
  }
}
