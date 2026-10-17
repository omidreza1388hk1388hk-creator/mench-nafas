import {
  Body,
  Controller,
  Get,
  Headers,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import {
  IsArray,
  IsBoolean,
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
} from 'class-validator';
import { AdminGuard, ModeratorGuard } from '../account/admin.guard';
import { SafetyService } from '../safety/safety.service';
import { GrowthService } from '../growth/growth.service';

class ResolveDto {
  @IsIn(['reviewing', 'actioned', 'dismissed']) status!: 'reviewing' | 'actioned' | 'dismissed';
  @IsOptional() @IsString() @MaxLength(2000) note?: string;
}
class RestrictDto {
  @IsIn(['mute', 'suspend']) kind!: 'mute' | 'suspend';
  @IsOptional() @IsInt() @Min(1) @Max(24 * 365) hours?: number;
  @IsString() @MaxLength(500) reason!: string;
}
class AppealResolveDto {
  @IsBoolean() overturn!: boolean;
}
class RuleDto {
  @IsString() key!: string;
  @IsBoolean() enabled!: boolean;
  @IsInt() @Min(1) @Max(10000) threshold!: number;
  @IsInt() @Min(1) @Max(86400) windowSeconds!: number;
  @IsInt() @Min(1) @Max(24 * 30) actionHours!: number;
}
class RefCfgDto {
  @IsBoolean() enabled!: boolean;
  @IsInt() @Min(1) @Max(10000) qualifyMessages!: number;
  @IsInt() @Min(1) @Max(100) qualifyConversations!: number;
  @IsInt() @Min(1) @Max(365) rewardPlusDays!: number;
  @IsInt() @Min(1) @Max(1000) maxRewardsPerUser!: number;
}
class GrantDto {
  @IsUUID() userId!: string;
  @IsInt() @Min(1) @Max(3650) days!: number;
}
class CodesDto {
  @IsInt() @Min(1) @Max(3650) days!: number;
  @IsInt() @Min(1) @Max(200) count!: number;
  @IsInt() @Min(1) @Max(10000) maxUses!: number;
  @IsOptional() @IsString() expiresAt?: string;
}
class PackDto {
  @IsOptional() @IsUUID() id?: string;
  @IsString() @MaxLength(60) name!: string;
  @IsBoolean() premium!: boolean;
  @IsBoolean() active!: boolean;
  @IsInt() sort!: number;
  @IsArray() @IsString({ each: true }) glyphs!: string[];
}

const actorOf = (h?: string) =>
  `admin:${(h ?? 'unnamed').replace(/[^A-Za-z0-9_.-]/g, '').slice(0, 30) || 'unnamed'}`;
const lim = (v: string | undefined, d = 50) => Math.min(Math.max(Number(v) || d, 1), 200);

/**
 * Moderation + growth admin API. Moderation routes accept the moderator key; rules, rewards, Plus and
 * sticker packs need the admin key. Every state-changing call is attributed to x-admin-actor in the audit log.
 */
@Controller('admin/api')
export class EcosystemAdminController {
  constructor(
    private readonly safety: SafetyService,
    private readonly growth: GrowthService,
  ) {}

  @UseGuards(ModeratorGuard) @Get('reports') reports(
    @Query('status') status?: string,
    @Query('limit') limit?: string,
  ) {
    return this.safety.adminReports(status || undefined, lim(limit));
  }
  @UseGuards(ModeratorGuard) @Get('reports/:id') report(
    @Param('id', ParseUUIDPipe) id: string,
    @Headers('x-admin-actor') a?: string,
  ) {
    return this.safety.adminReportDetail(id, actorOf(a));
  }
  @UseGuards(ModeratorGuard)
  @Post('reports/:id/resolve')
  @HttpCode(204)
  async resolve(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: ResolveDto,
    @Headers('x-admin-actor') a?: string,
  ) {
    await this.safety.adminResolveReport(id, actorOf(a), dto.status, dto.note);
  }

  @UseGuards(ModeratorGuard)
  @Post('users/:id/restrict')
  restrict(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: RestrictDto,
    @Headers('x-admin-actor') a?: string,
  ) {
    return this.safety.adminRestrict(id, actorOf(a), dto.kind, dto.hours ?? null, dto.reason);
  }
  @UseGuards(ModeratorGuard) @Get('restrictions') restrictions(@Query('userId') userId?: string) {
    return this.safety.adminRestrictions(userId || undefined);
  }
  @UseGuards(ModeratorGuard)
  @Post('restrictions/:id/lift')
  @HttpCode(204)
  async lift(@Param('id', ParseUUIDPipe) id: string, @Headers('x-admin-actor') a?: string) {
    await this.safety.adminLift(id, actorOf(a));
  }

  @UseGuards(ModeratorGuard) @Get('appeals') appeals(@Query('status') status?: string) {
    return this.safety.adminAppeals(status || undefined);
  }
  @UseGuards(ModeratorGuard)
  @Post('appeals/:id/resolve')
  @HttpCode(204)
  async resolveAppeal(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: AppealResolveDto,
    @Headers('x-admin-actor') a?: string,
  ) {
    await this.safety.adminResolveAppeal(id, actorOf(a), dto.overturn);
  }

  @UseGuards(AdminGuard) @Get('rules') rules() {
    return this.safety.adminRules();
  }
  @UseGuards(AdminGuard)
  @Post('rules')
  @HttpCode(204)
  async saveRule(@Body() dto: RuleDto, @Headers('x-admin-actor') a?: string) {
    await this.safety.adminSaveRule(actorOf(a), dto);
  }
  @UseGuards(ModeratorGuard) @Get('audit') audit(@Query('limit') limit?: string) {
    return this.safety.adminAudit(lim(limit, 100));
  }

  @UseGuards(AdminGuard) @Get('referral-config') refCfg() {
    return this.growth.adminReferralConfig();
  }
  @UseGuards(AdminGuard)
  @Post('referral-config')
  @HttpCode(204)
  async saveRefCfg(@Body() dto: RefCfgDto) {
    await this.growth.adminSaveReferralConfig(dto);
  }

  @UseGuards(AdminGuard)
  @Post('plus/grant')
  @HttpCode(204)
  async grant(@Body() dto: GrantDto) {
    await this.growth.adminGrantPlus(dto.userId, dto.days);
  }
  @UseGuards(AdminGuard) @Post('plus/codes') codes(@Body() dto: CodesDto) {
    return this.growth.adminCreateCodes(dto.days, dto.count, dto.maxUses, dto.expiresAt);
  }
  @UseGuards(AdminGuard) @Get('sticker-packs') packs() {
    return this.growth.adminPacks();
  }
  @UseGuards(AdminGuard)
  @Post('sticker-packs')
  @HttpCode(204)
  async savePack(@Body() dto: PackDto) {
    await this.growth.adminSavePack(dto);
  }
}
