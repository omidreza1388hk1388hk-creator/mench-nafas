import {
  Body,
  Controller,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  Res,
  UseGuards,
  NotFoundException,
} from '@nestjs/common';
import { Response } from 'express';
import { IsBoolean, IsIn, IsInt, IsOptional, IsString, Max, MaxLength, Min } from 'class-validator';
import { AccountRepository } from './account.repository';
import { AdminGuard, ModeratorGuard } from './admin.guard';
import { ADMIN_PANEL_HTML } from './admin-panel.html';

class AnnouncementDto {
  @IsString() @MaxLength(120) title!: string;
  @IsString() @MaxLength(1000) body!: string;
  @IsIn(['info', 'warning', 'critical']) level!: 'info' | 'warning' | 'critical';
  @IsOptional() @IsString() endsAt?: string;
}
class ActiveDto {
  @IsBoolean() active!: boolean;
}
class FlagDto {
  @IsString() @MaxLength(64) key!: string;
  @IsBoolean() enabled!: boolean;
  @IsInt() @Min(0) @Max(100) rolloutPct!: number;
  @IsOptional() @IsString() @MaxLength(200) description?: string;
}

/** Served at /api/v1/admin (panel) and /api/v1/admin/api/* (JSON). Disabled (404) unless ADMIN_API_KEY is set. */
@Controller('admin')
export class AdminController {
  constructor(private readonly repo: AccountRepository) {}

  /** The panel page itself carries no data, so it is not key-protected; every data call is. */
  @Get()
  panel(@Res() res: Response) {
    if (!process.env.ADMIN_API_KEY) throw new NotFoundException();
    res.set({
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-store',
      'X-Frame-Options': 'DENY',
    });
    res.send(ADMIN_PANEL_HTML);
  }

  @UseGuards(AdminGuard)
  @Get('api/stats')
  stats() {
    return this.repo.adminStats();
  }

  @UseGuards(ModeratorGuard)
  @Get('api/users')
  users(@Query('q') q?: string, @Query('limit') limit?: string) {
    return this.repo.adminUsers(
      q?.trim() || undefined,
      Math.min(Math.max(Number(limit) || 50, 1), 200),
    );
  }

  @UseGuards(AdminGuard)
  @Get('api/feedback')
  feedback(@Query('limit') limit?: string) {
    return this.repo.adminFeedback(Math.min(Math.max(Number(limit) || 50, 1), 200));
  }

  @UseGuards(AdminGuard)
  @Get('api/announcements')
  announcements() {
    return this.repo.adminListAnnouncements();
  }

  @UseGuards(AdminGuard)
  @Post('api/announcements')
  addAnnouncement(@Body() dto: AnnouncementDto) {
    return this.repo.adminSaveAnnouncement(dto);
  }

  @UseGuards(AdminGuard)
  @Post('api/announcements/:id/active')
  @HttpCode(204)
  async setActive(@Param('id', ParseUUIDPipe) id: string, @Body() dto: ActiveDto) {
    await this.repo.adminSetAnnouncementActive(id, dto.active);
  }

  @UseGuards(AdminGuard)
  @Get('api/flags')
  flags() {
    return this.repo.allFlags();
  }

  @UseGuards(AdminGuard)
  @Post('api/flags')
  @HttpCode(204)
  async saveFlag(@Body() dto: FlagDto) {
    await this.repo.adminUpsertFlag(dto);
  }

  @UseGuards(AdminGuard)
  @Get('api/analytics')
  analytics(@Query('days') days?: string) {
    return this.repo.adminAnalytics(Math.min(Math.max(Number(days) || 14, 1), 90));
  }
}
