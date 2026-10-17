import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Post, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { CreateVaultItemDto } from './dto/create-vault-item.dto';
import { VaultService } from './vault.service';

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId/vault')
export class VaultController {
  constructor(private readonly vaultService: VaultService) {}

  @Get()
  list(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.vaultService.list(conversationId, req.user.sub);
  }

  @Post()
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  create(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: CreateVaultItemDto,
  ) {
    return this.vaultService.create(conversationId, req.user.sub, dto.blob);
  }

  @Delete(':itemId')
  @HttpCode(204)
  async remove(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('itemId', ParseUUIDPipe) itemId: string,
  ) {
    await this.vaultService.remove(conversationId, itemId, req.user.sub);
  }
}
