import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Post, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { CreateAlbumItemDto, ReactAlbumItemDto } from './dto/album.dto';
import { AlbumService } from './album.service';

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/album')
export class AlbumController {
  constructor(private readonly albumService: AlbumService) {}

  @Get()
  list(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.albumService.list(conversationId, req.user.sub);
  }

  @Post()
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  create(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string, @Body() dto: CreateAlbumItemDto) {
    return this.albumService.create(conversationId, req.user.sub, dto);
  }

  @Post(':itemId/react')
  @HttpCode(204)
  @Throttle({ default: { limit: 120, ttl: 60_000 } })
  async react(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('itemId', ParseUUIDPipe) itemId: string,
    @Body() dto: ReactAlbumItemDto,
  ) {
    await this.albumService.react(conversationId, itemId, req.user.sub, dto.emoji ?? null);
  }

  @Delete(':itemId')
  @HttpCode(204)
  async remove(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('itemId', ParseUUIDPipe) itemId: string,
  ) {
    await this.albumService.remove(conversationId, itemId, req.user.sub);
  }
}
