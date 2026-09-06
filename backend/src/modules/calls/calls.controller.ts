import { Body, Controller, Get, Param, ParseUUIDPipe, Post, Query, Req, UseGuards } from '@nestjs/common';
import { CallsService } from './calls.service';
import { InitiateCallDto } from './dto/initiate-call.dto';
import { EndCallDto } from './dto/end-call.dto';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

@UseGuards(JwtAuthGuard)
@Controller('calls')
export class CallsController {
  constructor(private readonly callsService: CallsService) {}

  @Post()
  initiate(@Req() req: any, @Body() dto: InitiateCallDto) {
    return this.callsService.initiate(req.user.sub, dto.conversationId, dto.callType);
  }

  @Get()
  listHistory(@Req() req: any, @Query('before') before?: string, @Query('limit') limit?: string) {
    const parsedLimit = limit ? Math.min(100, Math.max(1, parseInt(limit, 10))) : 50;
    return this.callsService.listHistory(req.user.sub, before, parsedLimit);
  }

  @Get(':callId')
  get(@Req() req: any, @Param('callId', ParseUUIDPipe) callId: string) {
    return this.callsService.get(callId, req.user.sub);
  }

  @Post(':callId/accept')
  accept(@Req() req: any, @Param('callId', ParseUUIDPipe) callId: string) {
    return this.callsService.accept(callId, req.user.sub);
  }

  @Post(':callId/decline')
  async decline(@Req() req: any, @Param('callId', ParseUUIDPipe) callId: string) {
    await this.callsService.decline(callId, req.user.sub);
    return { ok: true };
  }

  @Post(':callId/end')
  async end(@Req() req: any, @Param('callId', ParseUUIDPipe) callId: string, @Body() dto: EndCallDto) {
    await this.callsService.end(callId, req.user.sub, dto.reason);
    return { ok: true };
  }
}
