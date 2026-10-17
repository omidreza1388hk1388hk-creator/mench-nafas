import { Controller, Get, Param, ParseIntPipe, Res, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import type { Response } from 'express';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { MapsService } from './maps.service';

@UseGuards(JwtAuthGuard)
@Controller('maps')
export class MapsController {
  constructor(private readonly mapsService: MapsService) {}

  /** GET /maps/tile/:z/:x/:y → PNG tile (proxied + cached). */
  @Get('tile/:z/:x/:y')
  @Throttle({ default: { limit: 400, ttl: 60_000 } })
  async tile(
    @Param('z', ParseIntPipe) z: number,
    @Param('x', ParseIntPipe) x: number,
    @Param('y', ParseIntPipe) y: number,
    @Res() res: Response,
  ): Promise<void> {
    const tile = await this.mapsService.getTile(z, x, y);
    res.set({
      'Content-Type': tile.contentType,
      'Content-Length': tile.buffer.length.toString(),
      'Cache-Control': 'private, max-age=604800',
    });
    res.send(tile.buffer);
  }
}
