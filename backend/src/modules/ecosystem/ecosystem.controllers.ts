import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post, Query, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { ArrayMaxSize, IsArray, IsBoolean, IsIn, IsInt, IsOptional, IsString, IsUUID, Max, MaxLength, Min } from 'class-validator';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { SafetyService } from '../safety/safety.service';
import { GrowthService } from '../growth/growth.service';
import { CloudService } from '../cloud/cloud.service';
import { PlannerService } from '../planner/planner.service';

class ReportDto {
  @IsOptional() @IsUUID() targetUserId?: string;
  @IsOptional() @IsUUID() conversationId?: string;
  @IsOptional() @IsUUID() messageId?: string;
  @IsString() category!: string;
  @IsOptional() @IsString() @MaxLength(1000) details?: string;
}
class AppealDto {
  @IsUUID() restrictionId!: string;
  @IsString() @MaxLength(1000) message!: string;
}
class CodeDto {
  @IsString() @MaxLength(40) code!: string;
}
class SaveMessageDto {
  @IsUUID() messageId!: string;
  @IsOptional() @IsString() @MaxLength(40) folder?: string;
}
class NoteDto {
  @IsString() @MaxLength(8000) body!: string;
  @IsOptional() @IsString() @MaxLength(40) folder?: string;
  @IsOptional() @IsArray() @ArrayMaxSize(10) @IsString({ each: true }) tags?: string[];
}
class SavedPatchDto {
  @IsOptional() @IsString() @MaxLength(40) folder?: string | null;
  @IsOptional() @IsArray() @ArrayMaxSize(10) @IsString({ each: true }) tags?: string[];
}
class ChecklistItemDto {
  @IsString() @MaxLength(200) text!: string;
  @IsBoolean() done!: boolean;
}
class PlannerDto {
  @IsOptional() @IsIn(['reminder', 'task', 'event']) kind?: 'reminder' | 'task' | 'event';
  @IsString() @MaxLength(200) title!: string;
  @IsOptional() @IsString() @MaxLength(2000) note?: string;
  @IsOptional() @IsString() dueAt?: string | null;
  @IsOptional() @IsIn(['none', 'daily', 'weekly', 'monthly']) repeat?: 'none' | 'daily' | 'weekly' | 'monthly';
  @IsOptional() @IsInt() @Min(0) @Max(10080) remindMinutesBefore?: number;
  @IsOptional() @IsString() @MaxLength(200) location?: string;
  @IsOptional() @IsArray() @ArrayMaxSize(50) checklist?: ChecklistItemDto[];
  @IsOptional() @IsArray() @ArrayMaxSize(20) @IsString({ each: true }) @MaxLength(60, { each: true }) participants?: string[];
  @IsOptional() @IsUUID() conversationId?: string;
  @IsOptional() @IsUUID() messageId?: string;
}
class PlannerPatchDto {
  @IsOptional() @IsString() @MaxLength(200) title?: string;
  @IsOptional() @IsString() @MaxLength(2000) note?: string;
  @IsOptional() @IsString() dueAt?: string | null;
  @IsOptional() @IsIn(['none', 'daily', 'weekly', 'monthly']) repeat?: 'none' | 'daily' | 'weekly' | 'monthly';
  @IsOptional() @IsInt() @Min(0) @Max(10080) remindMinutesBefore?: number;
  @IsOptional() @IsString() @MaxLength(200) location?: string;
  @IsOptional() @IsArray() @ArrayMaxSize(50) checklist?: ChecklistItemDto[];
  @IsOptional() @IsArray() @ArrayMaxSize(20) @IsString({ each: true }) @MaxLength(60, { each: true }) participants?: string[];
}
class DoneDto {
  @IsBoolean() done!: boolean;
}

const num = (v: string | undefined, d: number, min: number, max: number) => Math.min(Math.max(Number(v) || d, min), max);

@UseGuards(JwtAuthGuard)
@Controller()
export class SafetyController {
  constructor(private readonly safety: SafetyService) {}

  @Post('reports') @Throttle({ default: { limit: 10, ttl: 3_600_000 } })
  report(@Req() req: any, @Body() dto: ReportDto) { return this.safety.createReport(req.user.sub, dto); }

  @Get('safety/restriction')
  restriction(@Req() req: any) { return this.safety.myRestriction(req.user.sub); }

  @Post('safety/appeals') @HttpCode(204)
  async appeal(@Req() req: any, @Body() dto: AppealDto) { await this.safety.createAppeal(req.user.sub, dto.restrictionId, dto.message); }

  @Get('transparency')
  transparency() { return this.safety.transparency(); }
}

@UseGuards(JwtAuthGuard)
@Controller()
export class GrowthController {
  constructor(private readonly growth: GrowthService) {}

  @Get('plus/status') plus(@Req() req: any) { return this.growth.plusStatus(req.user.sub); }

  /** "Restore purchases": the server is the single source of truth, so restoring is simply re-reading it. */
  @Post('plus/restore') restore(@Req() req: any) { return this.growth.plusStatus(req.user.sub); }

  @Post('plus/redeem') @Throttle({ default: { limit: 10, ttl: 3_600_000 } })
  redeem(@Req() req: any, @Body() dto: CodeDto) { return this.growth.redeem(req.user.sub, dto.code); }

  @Get('stickers/packs') packs(@Req() req: any) { return this.growth.stickerPacks(req.user.sub); }

  @Get('referrals/mine') mine(@Req() req: any) { return this.growth.myReferrals(req.user.sub); }

  @Post('referrals/claim') @HttpCode(204) @Throttle({ default: { limit: 5, ttl: 3_600_000 } })
  async claim(@Req() req: any, @Body() dto: CodeDto) { await this.growth.claim(req.user.sub, dto.code); }
}

@UseGuards(JwtAuthGuard)
@Controller()
export class CloudController {
  constructor(private readonly cloud: CloudService) {}

  @Post('saved/message') saveMessage(@Req() req: any, @Body() dto: SaveMessageDto) { return this.cloud.saveMessage(req.user.sub, dto.messageId, dto.folder); }
  @Post('saved/note') note(@Req() req: any, @Body() dto: NoteDto) { return this.cloud.addNote(req.user.sub, dto.body, dto.folder, dto.tags); }

  @Get('saved')
  list(@Req() req: any, @Query('q') q?: string, @Query('folder') folder?: string, @Query('tag') tag?: string, @Query('kind') kind?: string, @Query('limit') limit?: string, @Query('offset') offset?: string) {
    return this.cloud.list(req.user.sub, { q, folder, tag, kind, limit: num(limit, 50, 1, 200), offset: num(offset, 0, 0, 100000) });
  }

  @Get('saved/folders') folders(@Req() req: any) { return this.cloud.folders(req.user.sub); }
  @Get('saved/usage') usage(@Req() req: any) { return this.cloud.usage(req.user.sub); }

  @Patch('saved/:id') @HttpCode(204)
  async patch(@Req() req: any, @Param('id', ParseUUIDPipe) id: string, @Body() dto: SavedPatchDto) { await this.cloud.update(req.user.sub, id, dto); }

  @Delete('saved/:id') @HttpCode(204)
  async remove(@Req() req: any, @Param('id', ParseUUIDPipe) id: string) { await this.cloud.remove(req.user.sub, id); }

  @Get('files')
  files(@Req() req: any, @Query('kind') kind?: string, @Query('conversationId') conversationId?: string, @Query('senderId') senderId?: string, @Query('q') q?: string,
    @Query('sort') sort?: string, @Query('dir') dir?: string, @Query('limit') limit?: string, @Query('offset') offset?: string) {
    return this.cloud.files(req.user.sub, {
      kind, conversationId, senderId, q,
      sort: sort === 'size' || sort === 'name' ? sort : 'date', dir: dir === 'asc' ? 'asc' : 'desc',
      limit: num(limit, 50, 1, 200), offset: num(offset, 0, 0, 100000),
    });
  }
}

@UseGuards(JwtAuthGuard)
@Controller('planner')
export class PlannerController {
  constructor(private readonly planner: PlannerService) {}

  @Post() create(@Req() req: any, @Body() dto: PlannerDto) { return this.planner.create(req.user.sub, dto); }

  @Get()
  list(@Req() req: any, @Query('filter') filter?: string, @Query('tzOffsetMinutes') tz?: string) {
    const f = (['overdue', 'today', 'upcoming', 'completed'] as const).find((x) => x === filter) ?? 'all';
    return this.planner.list(req.user.sub, f, Number(tz) || 0);
  }

  @Patch(':id') update(@Req() req: any, @Param('id', ParseUUIDPipe) id: string, @Body() dto: PlannerPatchDto) { return this.planner.update(req.user.sub, id, dto); }
  @Post(':id/complete') complete(@Req() req: any, @Param('id', ParseUUIDPipe) id: string, @Body() dto: DoneDto) { return this.planner.complete(req.user.sub, id, dto.done); }

  @Delete(':id') @HttpCode(204)
  async remove(@Req() req: any, @Param('id', ParseUUIDPipe) id: string) { await this.planner.remove(req.user.sub, id); }
}
