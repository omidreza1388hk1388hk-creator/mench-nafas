import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Post, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { CreateScheduledMessageDto } from './dto/create-scheduled-message.dto';
import { ScheduledMessagesService } from './scheduled-messages.service';

@UseGuards(JwtAuthGuard)
@Controller()
export class ScheduledMessagesController {
  constructor(private readonly service: ScheduledMessagesService) {}

  @Post('conversations/:conversationId/scheduled')
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  schedule(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string, @Body() dto: CreateScheduledMessageDto) {
    return this.service.schedule({ conversationId, senderId: req.user.sub, clientMsgId: dto.clientMsgId, body: dto.body, sendAt: dto.sendAt, silent: dto.silent });
  }

  @Get('conversations/:conversationId/scheduled')
  list(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.service.list(conversationId, req.user.sub);
  }

  @Delete('scheduled/:id')
  @HttpCode(204)
  async cancel(@Req() req: any, @Param('id', ParseUUIDPipe) id: string): Promise<void> {
    await this.service.cancel(id, req.user.sub);
  }
}
