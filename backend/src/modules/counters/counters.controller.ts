import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { BumpCounterDto } from './dto/bump-counter.dto';
import { CreateCounterDto } from './dto/create-counter.dto';
import { CountersService } from './counters.service';

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/counters')
export class CountersController {
  constructor(private readonly countersService: CountersService) {}

  @Get()
  list(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.countersService.list(conversationId, req.user.sub);
  }

  @Post()
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  create(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: CreateCounterDto,
  ) {
    return this.countersService.create(conversationId, req.user.sub, dto);
  }

  @Post(':counterId/bump')
  @Throttle({ default: { limit: 60, ttl: 60_000 } })
  bump(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('counterId', ParseUUIDPipe) counterId: string,
    @Body() dto: BumpCounterDto,
  ) {
    return this.countersService.bump(conversationId, counterId, req.user.sub, dto.delta);
  }

  @Delete(':counterId')
  @HttpCode(204)
  async remove(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('counterId', ParseUUIDPipe) counterId: string,
  ) {
    await this.countersService.remove(conversationId, counterId, req.user.sub);
  }
}
