import { Body, Controller, Delete, Get, Param, ParseUUIDPipe, Patch, Post, Req, UseGuards } from '@nestjs/common';
import { CreateDirectConversationDto } from './dto/create-direct-conversation.dto';
import { CreateGroupDto } from './dto/create-group.dto';
import { AddMembersDto } from './dto/add-members.dto';
import { RenameGroupDto } from './dto/rename-group.dto';
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
