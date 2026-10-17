import { Body, Controller, HttpCode, Post, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { IsIn, IsString, MaxLength, MinLength } from 'class-validator';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { TRANSLATE_TARGETS, TranslateService, TranslateTarget } from './translate.service';

class TranslateDto {
  @IsString() @MinLength(1) @MaxLength(4000) text!: string;
  @IsIn(TRANSLATE_TARGETS as unknown as string[]) target!: TranslateTarget;
}

@UseGuards(JwtAuthGuard)
@Controller('translate')
export class TranslateController {
  constructor(private readonly service: TranslateService) {}

  @Post()
  @HttpCode(200)
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  translate(@Body() dto: TranslateDto) {
    return this.service.translate(dto.text, dto.target);
  }
}
