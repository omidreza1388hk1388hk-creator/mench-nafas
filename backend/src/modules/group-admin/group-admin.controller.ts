import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post, Req, UseGuards } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';
import { ChangeRoleDto, CreateInviteDto, TransferOwnerDto, UpdateGroupSettingsDto } from './dto/group-admin.dto';
import { GroupAdminService } from './group-admin.service';

@UseGuards(JwtAuthGuard)
@Controller()
export class GroupAdminController {
  constructor(private readonly service: GroupAdminService) {}

  @Get('conversations/:conversationId/group')
  info(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.service.getInfo(conversationId, req.user.sub);
  }

  @Patch('conversations/:conversationId/group')
  update(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string, @Body() dto: UpdateGroupSettingsDto) {
    return this.service.updateSettings(conversationId, req.user.sub, { ...dto });
  }

  @Patch('conversations/:conversationId/members/:userId/role')
  @HttpCode(204)
  async changeRole(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('userId', ParseUUIDPipe) userId: string,
    @Body() dto: ChangeRoleDto,
  ): Promise<void> {
    await this.service.changeRole(conversationId, req.user.sub, userId, dto.role);
  }

  @Post('conversations/:conversationId/transfer-owner')
  @HttpCode(204)
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  async transferOwner(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: TransferOwnerDto,
  ): Promise<void> {
    await this.service.transferOwnership(conversationId, req.user.sub, dto.userId);
  }

  @Post('conversations/:conversationId/invites')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  createInvite(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string, @Body() dto: CreateInviteDto) {
    return this.service.createInvite(conversationId, req.user.sub, dto);
  }

  @Get('conversations/:conversationId/invites')
  listInvites(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.service.listInvites(conversationId, req.user.sub);
  }

  @Delete('conversations/:conversationId/invites/:inviteId')
  @HttpCode(204)
  async revoke(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('inviteId', ParseUUIDPipe) inviteId: string,
  ): Promise<void> {
    await this.service.revokeInvite(conversationId, req.user.sub, inviteId);
  }

  /** Anyone signed in can look a code up (to see what they'd be joining) — throttled so codes can't be guessed. */
  @Get('group-invites/:code')
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  preview(@Param('code') code: string) {
    return this.service.preview(code);
  }

  @Post('group-invites/:code/join')
  @Throttle({ default: { limit: 15, ttl: 60_000 } })
  join(@Req() req: any, @Param('code') code: string) {
    return this.service.join(code, req.user.sub);
  }
}
