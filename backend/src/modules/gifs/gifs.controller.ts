import { BadRequestException, Controller, Get, Param, Query, Res, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import type { Response } from 'express';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { GifsService } from './gifs.service';

@UseGuards(JwtAuthGuard)
@Controller('gifs')
export class GifsController {
  constructor(private readonly gifsService: GifsService) {}

  /** GET /gifs/search?q=love&offset=0 → { items: [{id,title,width,height}], nextOffset } */
  @Get('search')
  @Throttle({ default: { limit: 60, ttl: 60_000 } })
  search(@Query('q') q: string, @Query('offset') offset?: string) {
    return this.gifsService.search(q ?? '', parseOffset(offset));
  }

  /** GET /gifs/trending?offset=0 — same shape as search. */
  @Get('trending')
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  trending(@Query('offset') offset?: string) {
    return this.gifsService.trending(parseOffset(offset));
  }

  /** GET /gifs/:id/media?size=preview|full — the GIF bytes, proxied (see GifsService). */
  @Get(':id/media')
  @Throttle({ default: { limit: 300, ttl: 60_000 } })
  async media(@Param('id') id: string, @Query('size') size: string | undefined, @Res() res: Response): Promise<void> {
    if (size !== undefined && size !== 'preview' && size !== 'full') throw new BadRequestException('size must be preview or full');
    const media = await this.gifsService.getMedia(id, size ?? 'full');
    res.set({
      'Content-Type': media.contentType,
      'Content-Length': media.buffer.length.toString(),
      'Cache-Control': 'private, max-age=31536000, immutable',
    });
    res.send(media.buffer);
  }
}

function parseOffset(raw: string | undefined): number {
  if (raw === undefined || raw === '') return 0;
  const n = Number(raw);
  if (!Number.isInteger(n) || n < 0) throw new BadRequestException('offset must be a non-negative integer');
  return n;
}
