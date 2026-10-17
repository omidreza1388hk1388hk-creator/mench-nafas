import { Body, Controller, Get, Param, ParseUUIDPipe, Post, Put, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { CreatePollDto } from './dto/create-poll.dto';
import { VotePollDto } from './dto/vote-poll.dto';
import { PollsService } from './polls.service';

@UseGuards(JwtAuthGuard)
@Controller()
export class PollsController {
  constructor(private readonly pollsService: PollsService) {}

  @Post('conversations/:conversationId/polls')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  create(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string, @Body() dto: CreatePollDto) {
    return this.pollsService.create({
      conversationId,
      creatorId: req.user.sub,
      question: dto.question,
      options: dto.options,
      multiple: dto.multiple,
    });
  }

  @Get('polls/:pollId')
  get(@Req() req: any, @Param('pollId', ParseUUIDPipe) pollId: string) {
    return this.pollsService.get(pollId, req.user.sub);
  }

  @Put('polls/:pollId/vote')
  @Throttle({ default: { limit: 60, ttl: 60_000 } })
  vote(@Req() req: any, @Param('pollId', ParseUUIDPipe) pollId: string, @Body() dto: VotePollDto) {
    return this.pollsService.vote(pollId, req.user.sub, dto.optionIds);
  }

  @Post('polls/:pollId/close')
  close(@Req() req: any, @Param('pollId', ParseUUIDPipe) pollId: string) {
    return this.pollsService.close(pollId, req.user.sub);
  }
}
