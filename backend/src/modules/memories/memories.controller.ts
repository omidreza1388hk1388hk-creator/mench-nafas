import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Post, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { CreateMemoryDto } from './dto/create-memory.dto';
import { MemoriesService } from './memories.service';

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/memories')
export class MemoriesController {
  constructor(private readonly memoriesService: MemoriesService) {}

  @Get()
  list(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.memoriesService.list(conversationId, req.user.sub);
  }

  @Post()
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  create(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: CreateMemoryDto,
  ) {
    return this.memoriesService.create(conversationId, req.user.sub, dto);
  }

  @Delete(':memoryId')
  @HttpCode(204)
  async remove(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('memoryId', ParseUUIDPipe) memoryId: string,
  ) {
    await this.memoriesService.remove(conversationId, memoryId, req.user.sub);
  }
}
