import { Body, Controller, Delete, Get, Param, ParseUUIDPipe, Patch, Post, Query, Req, UseGuards } from '@nestjs/common';
import { SendMessageDto } from './dto/send-message.dto';
import { ListMessagesQueryDto } from './dto/list-messages-query.dto';
import { MarkReadDto } from './dto/mark-read.dto';
import { EditMessageDto } from './dto/edit-message.dto';
import { ReactMessageDto } from './dto/react-message.dto';
import { ForwardMessageDto } from './dto/forward-message.dto';
import { MessagesService } from './messages.service';
import { JwtAuthGuard } from '../sessions/jwt-auth.guard';

@UseGuards(JwtAuthGuard)
@Controller('conversations/:conversationId')
export class MessagesController {
  constructor(private readonly messagesService: MessagesService) {}

  @Post('messages')
  send(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: SendMessageDto,
  ) {
    return this.messagesService.send({
      conversationId,
      senderId: req.user.sub,
      clientMsgId: dto.clientMsgId,
      body: dto.body,
      attachmentId: dto.attachmentId,
    });
  }

  @Get('messages')
  list(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Query() query: ListMessagesQueryDto,
  ) {
    return this.messagesService.list(
      conversationId,
      req.user.sub,
      query.after ?? 0,
      query.limit ?? 50,
    );
  }

  @Post('read')
  async markRead(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: MarkReadDto,
  ) {
    await this.messagesService.markRead(conversationId, req.user.sub, dto.lastReadSequence);
    return { ok: true };
  }

  @Patch('messages/:messageId')
  editMessage(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('messageId', ParseUUIDPipe) messageId: string,
    @Body() dto: EditMessageDto,
  ) {
    return this.messagesService.edit(conversationId, messageId, req.user.sub, dto.body);
  }

  @Delete('messages/:messageId')
  async deleteMessage(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('messageId', ParseUUIDPipe) messageId: string,
  ) {
    await this.messagesService.delete(conversationId, messageId, req.user.sub);
    return { ok: true };
  }

  @Post('messages/:messageId/reactions')
  reactToMessage(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('messageId', ParseUUIDPipe) messageId: string,
    @Body() dto: ReactMessageDto,
  ) {
    return this.messagesService.react(conversationId, messageId, req.user.sub, dto.emoji);
  }

  @Delete('messages/:messageId/reactions')
  clearReaction(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Param('messageId', ParseUUIDPipe) messageId: string,
  ) {
    return this.messagesService.unreact(conversationId, messageId, req.user.sub);
  }

  /**
   * :conversationId here is the TARGET conversation (the one the
   * forwarded copy is sent into) — the source conversation is looked up
   * server-side from sourceMessageId, and membership in both is checked
   * (see MessagesService.forward), so the client never has to supply the
   * source conversation id itself.
   */
  @Post('messages/forward')
  forwardMessage(
    @Req() req: any,
    @Param('conversationId', ParseUUIDPipe) conversationId: string,
    @Body() dto: ForwardMessageDto,
  ) {
    return this.messagesService.forward(conversationId, dto.sourceMessageId, req.user.sub, dto.clientMsgId);
  }
}
