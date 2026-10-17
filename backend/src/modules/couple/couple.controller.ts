import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post, Put, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { AnswerQuestionDto, CreateWishDto, UpdateWishDto } from './dto/couple.dto';
import { CoupleService } from './couple.service';

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId')
export class CoupleController {
  constructor(private readonly coupleService: CoupleService) {}

  @Get('question')
  getQuestion(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.coupleService.getQuestion(conversationId, req.user.sub);
  }

  @Put('question')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  async answer(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: AnswerQuestionDto,
  ) {
    await this.coupleService.answerQuestion(conversationId, req.user.sub, dto.answer);
    return { ok: true };
  }

  @Get('wishlist')
  listWishes(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.coupleService.listWishes(conversationId, req.user.sub);
  }

  @Post('wishlist')
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  createWish(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: CreateWishDto,
  ) {
    return this.coupleService.createWish(conversationId, req.user.sub, dto.title);
  }

  @Patch('wishlist/:wishId')
  updateWish(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('wishId', ParseUUIDPipe) wishId: string,
    @Body() dto: UpdateWishDto,
  ) {
    return this.coupleService.updateWish(conversationId, wishId, req.user.sub, dto.done ?? true);
  }

  @Delete('wishlist/:wishId')
  @HttpCode(204)
  async deleteWish(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('wishId', ParseUUIDPipe) wishId: string,
  ) {
    await this.coupleService.deleteWish(conversationId, wishId, req.user.sub);
  }
}
