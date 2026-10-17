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
import { AppendStrokesDto } from './dto/append-strokes.dto';
import { CanvasService } from './canvas.service';

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/canvas')
export class CanvasController {
  constructor(private readonly canvasService: CanvasService) {}

  @Get()
  get(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.canvasService.get(conversationId, req.user.sub);
  }

  @Post('strokes')
  @Throttle({ default: { limit: 120, ttl: 60_000 } })
  append(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: AppendStrokesDto,
  ) {
    return this.canvasService.append(conversationId, req.user.sub, dto.strokes);
  }

  @Delete()
  @HttpCode(204)
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  async clear(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    await this.canvasService.clear(conversationId, req.user.sub);
  }
}
