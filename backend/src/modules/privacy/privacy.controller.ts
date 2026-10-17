import { Body, Controller, Get, Param, ParseUUIDPipe, Put, Req, UseGuards } from '@nestjs/common';
import { PrivacyService } from './privacy.service';
import { UpdatePrivacyDto } from './dto/update-privacy.dto';
import { SetDisappearingDto } from './dto/set-disappearing.dto';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

@UseGuards(JwtAuthGuard)
@Controller('privacy')
export class PrivacyController {
  constructor(private readonly privacy: PrivacyService) {}

  @Get()
  get(@Req() req: any) {
    return this.privacy.get(req.user.sub);
  }

  @Put()
  update(@Req() req: any, @Body() dto: UpdatePrivacyDto) {
    return this.privacy.update(req.user.sub, dto);
  }
}

/** Mounted under /conversations so the URL reads naturally; kept out of ConversationsController on purpose. */
@UseGuards(JwtAuthGuard)
@Controller('conversations')
export class DisappearingController {
  constructor(private readonly privacy: PrivacyService) {}

  @Get(':conversationId/disappearing')
  get(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.privacy.getDisappearing(conversationId, req.user.sub);
  }

  @Put(':conversationId/disappearing')
  set(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: SetDisappearingDto,
  ) {
    return this.privacy.setDisappearing(conversationId, req.user.sub, dto.seconds);
  }
}
