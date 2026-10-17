import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Post, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { AnswerQuizDto, CreateQuizDto } from './dto/quiz.dto';
import { QuizzesService } from './quizzes.service';

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/quizzes')
export class QuizzesController {
  constructor(private readonly quizzesService: QuizzesService) {}

  @Get()
  list(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.quizzesService.list(conversationId, req.user.sub);
  }

  @Post()
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  create(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string, @Body() dto: CreateQuizDto) {
    return this.quizzesService.create(conversationId, req.user.sub, dto);
  }

  @Get(':quizId')
  get(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('quizId', ParseUUIDPipe) quizId: string,
  ) {
    return this.quizzesService.get(conversationId, quizId, req.user.sub);
  }

  @Post(':quizId/answer')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  answer(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('quizId', ParseUUIDPipe) quizId: string,
    @Body() dto: AnswerQuizDto,
  ) {
    return this.quizzesService.answer(conversationId, quizId, req.user.sub, dto.answers);
  }

  @Delete(':quizId')
  @HttpCode(204)
  async remove(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('quizId', ParseUUIDPipe) quizId: string,
  ) {
    await this.quizzesService.remove(conversationId, quizId, req.user.sub);
  }
}
