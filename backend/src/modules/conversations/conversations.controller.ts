import { Body, Controller, Delete, Get, Param, ParseUUIDPipe, Patch, Post, Req, UseGuards } from '@nestjs/common';
import { CreateDirectConversationDto } from './dto/create-direct-conversation.dto';
import { CreateGroupDto } from './dto/create-group.dto';
import { AddMembersDto } from './dto/add-members.dto';
import { RenameGroupDto } from './dto/rename-group.dto';
import { UpdateConversationSettingsDto } from './dto/update-conversation-settings.dto';
import { UpdateUsDto } from './dto/update-us.dto';
import { SendAffectionDto } from './dto/send-affection.dto';
import { SendHeartbeatDto } from './dto/send-heartbeat.dto';
import { SendScreenEffectDto } from './dto/send-screen-effect.dto';
import { SendTogetherDto } from './dto/send-together.dto';
import { UpdateMoodDto } from './dto/update-mood.dto';
import { PinMessageDto } from './dto/pin-message.dto';
import { Throttle } from '@nestjs/throttler';
import { ConversationsService } from './conversations.service';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

@UseGuards(JwtAuthGuard)
@Controller('conversations')
export class ConversationsController {
  constructor(private readonly conversationsService: ConversationsService) {}

  @Post('direct')
  async createDirect(@Req() req: any, @Body() dto: CreateDirectConversationDto) {
    const conversation = await this.conversationsService.createDirect(
      req.user.sub,
      dto.targetUserId,
    );
    return conversation;
  }

  /** Private Saved messages chat of the caller (created on first use). */
  @Post('saved')
  getSaved(@Req() req: any) {
    return this.conversationsService.getOrCreateSaved(req.user.sub);
  }

  @Post('group')
  createGroup(@Req() req: any, @Body() dto: CreateGroupDto) {
    return this.conversationsService.createGroup(req.user.sub, dto.title, dto.memberIds);
  }

  @Get()
  async list(@Req() req: any) {
    return this.conversationsService.listForUser(req.user.sub);
  }

  @Get(':conversationId/members')
  listMembers(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.conversationsService.listMembers(conversationId, req.user.sub);
  }

  @Post(':conversationId/members')
  async addMembers(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: AddMembersDto,
  ) {
    await this.conversationsService.addMembers(conversationId, req.user.sub, dto.memberIds);
    return { ok: true };
  }

  /** :userId === the caller's own id is how "leave group" is expressed — see ConversationsService.removeMember for the authorization split between that and removing someone else. */
  @Delete(':conversationId/members/:userId')
  async removeMember(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('userId', ParseUUIDPipe) userId: string,
  ) {
    await this.conversationsService.removeMember(conversationId, req.user.sub, userId);
    return { ok: true };
  }

  /** Per-user mute/pin for this conversation (never affects the other members). */
  @Patch(':conversationId/settings')
  async updateSettings(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: UpdateConversationSettingsDto,
  ) {
    await this.conversationsService.updateSettings(conversationId, req.user.sub, dto);
    return { ok: true };
  }

  @Patch(':conversationId/us')
  async updateUs(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: UpdateUsDto,
  ) {
    await this.conversationsService.updateUs(conversationId, req.user.sub, dto);
    return { ok: true };
  }

  @Post(':conversationId/miss-you')
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  async missYou(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    await this.conversationsService.sendMissYou(conversationId, req.user.sub);
    return { ok: true };
  }

  @Post(':conversationId/heartbeat')
  @Throttle({ default: { limit: 12, ttl: 60_000 } })
  async heartbeat(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: SendHeartbeatDto,
  ) {
    return this.conversationsService.sendHeartbeat(conversationId, req.user.sub, dto.durationMs);
  }

  @Post(':conversationId/together')
  @Throttle({ default: { limit: 240, ttl: 60_000 } })
  async together(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: SendTogetherDto,
  ) {
    await this.conversationsService.sendTogether(conversationId, req.user.sub, dto);
    return { ok: true };
  }

  @Post(':conversationId/screen-effect')
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  async screenEffect(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: SendScreenEffectDto,
  ) {
    await this.conversationsService.sendScreenEffect(conversationId, req.user.sub, dto.effect);
    return { ok: true };
  }

  @Patch(':conversationId/pin')
  async pinMessage(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: PinMessageDto,
  ) {
    await this.conversationsService.setPinnedMessage(conversationId, req.user.sub, dto.messageId ?? null);
    return { ok: true };
  }

  @Get(':conversationId/mood')
  getMood(@Req() req: any, @Param('conversationId', ParseUUIDPipe) conversationId: string) {
    return this.conversationsService.getMoods(conversationId, req.user.sub);
  }

  @Patch(':conversationId/mood')
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  async setMood(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: UpdateMoodDto,
  ) {
    await this.conversationsService.setMood(conversationId, req.user.sub, dto.emoji, dto.text);
    return { ok: true };
  }

  @Post(':conversationId/affection')
  @Throttle({ default: { limit: 30, ttl: 60_000 } })
  async affection(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: SendAffectionDto,
  ) {
    await this.conversationsService.sendAffection(conversationId, req.user.sub, dto.kind);
    return { ok: true };
  }

  @Patch(':conversationId')
  async renameGroup(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: RenameGroupDto,
  ) {
    await this.conversationsService.renameGroup(conversationId, req.user.sub, dto.title);
    return { ok: true };
  }
}
