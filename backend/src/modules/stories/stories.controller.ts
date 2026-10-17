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
import { CreateStoryDto } from './dto/create-story.dto';
import { StoriesService } from './stories.service';

@UseGuards(JwtAuthGuard)
@Controller('stories')
export class StoriesInboxController {
  constructor(private readonly storiesService: StoriesService) {}

  /** Which of my conversations have stories I have not seen yet (drives the rings on the chat list). */
  @Get('unseen')
  unseen(@Req() req: any) {
    return this.storiesService.unseen(req.user.sub);
  }
}

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/stories')
export class StoriesController {
  constructor(private readonly storiesService: StoriesService) {}

  @Get()
  list(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.storiesService.list(conversationId, req.user.sub);
  }

  @Post()
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  create(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: CreateStoryDto,
  ) {
    return this.storiesService.create(conversationId, req.user.sub, dto);
  }

  @Post(':storyId/view')
  @HttpCode(204)
  @Throttle({ default: { limit: 120, ttl: 60_000 } })
  async view(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('storyId', ParseUUIDPipe) storyId: string,
  ) {
    await this.storiesService.markViewed(conversationId, storyId, req.user.sub);
  }

  @Delete(':storyId')
  @HttpCode(204)
  async remove(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('storyId', ParseUUIDPipe) storyId: string,
  ) {
    await this.storiesService.remove(conversationId, storyId, req.user.sub);
  }
}
