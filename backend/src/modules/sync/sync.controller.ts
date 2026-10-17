import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  Put,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { IsString, MaxLength } from 'class-validator';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { SyncService } from './sync.service';

class PutSyncDto {
  @IsString() @MaxLength(8192) value!: string;
}

@UseGuards(JwtAuthGuard)
@Controller('sync/state')
export class SyncController {
  constructor(private readonly sync: SyncService) {}

  @Get() list(@Req() req: any, @Query('since') since?: string) {
    return this.sync.changedSince(req.user.sub, since);
  }

  @Get(':key') get(@Req() req: any, @Param('key') key: string) {
    return this.sync.get(req.user.sub, key);
  }

  @Put(':key') put(@Req() req: any, @Param('key') key: string, @Body() dto: PutSyncDto) {
    return this.sync.put(req.user.sub, key, dto.value);
  }

  @Delete(':key') @HttpCode(204) remove(@Req() req: any, @Param('key') key: string) {
    return this.sync.remove(req.user.sub, key);
  }
}
