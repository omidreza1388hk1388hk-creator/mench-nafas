import { Body, Controller, Get, Param, ParseUUIDPipe, Post, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { CreateLetterDto } from './dto/create-letter.dto';
import { LettersService } from './letters.service';

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/letters')
export class LettersController {
  constructor(private readonly lettersService: LettersService) {}

  @Get()
  list(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.lettersService.list(conversationId, req.user.sub);
  }

  @Post()
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  create(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: CreateLetterDto,
  ) {
    return this.lettersService.create(conversationId, req.user.sub, dto);
  }

  @Post(':letterId/open')
  open(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('letterId', ParseUUIDPipe) letterId: string,
  ) {
    return this.lettersService.open(conversationId, letterId, req.user.sub);
  }
}
