import { BadRequestException, ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { MessagesRepository, MessageRow } from './messages.repository';
import { ConversationsService } from '../conversations/conversations.service';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { AttachmentsService } from '../attachments/attachments.service';
import { REALTIME_BROADCASTER, RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import { MessageDto, ReactionSummary } from '../realtime/realtime-events';
import { UsersService } from '../users/users.service';
import { NotificationsService } from '../notifications/notifications.service';

/**
 * reactions defaults to [] rather than being required at every call site
 * — most callers (send, edit, delete) know a fresh/just-mutated message
 * can't have picked up reactions in the same instant, so passing [] is
 * both correct and saves a query. Only list() and react()/unreact()
 * actually fetch real reaction rows.
 */
function toDto(row: MessageRow, reactions: ReactionSummary[] = []): MessageDto {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    senderId: row.sender_id,
    clientMsgId: row.client_msg_id,
    kind: row.kind,
    body: row.body,
    attachment: row.attachment_id
      ? {
          id: row.attachment_id,
          kind: row.attachment_kind as string,
          mimeType: row.attachment_mime_type as string,
          originalFilename: row.attachment_original_filename as string,
          // BIGINT comes back from pg as a string — see MessageDto's
          // `sequence` field for the same reasoning; attachment sizes are
          // nowhere near Number.MAX_SAFE_INTEGER.
          sizeBytes: Number(row.attachment_size_bytes),
          widthPx: row.attachment_width_px,
          heightPx: row.attachment_height_px,
          durationMs: row.attachment_duration_ms,
          hasThumbnail: row.attachment_thumbnail_storage_key !== null,
        }
      : null,
    // BIGSERIAL comes back from pg as a string to avoid silent precision
    // loss; Phase 2's realistic message volume per conversation is nowhere
    // near Number.MAX_SAFE_INTEGER (2^53), so converting here is safe and
    // keeps the wire format a plain JSON number for the Android client.
    sequence: Number(row.sequence),
    createdAt: row.created_at.toISOString(),
    editedAt: row.edited_at ? row.edited_at.toISOString() : null,
    deletedAt: row.deleted_at ? row.deleted_at.toISOString() : null,
    forwardedFromMessageId: row.forwarded_from_message_id,
    reactions,
  };
}

export interface SendMessageInput {
  conversationId: string;
  senderId: string;
  clientMsgId: string;
  body?: string;
  attachmentId?: string;
  /** Set only when this send is actually a forward — see MessagesService.forward. */
  forwardedFromMessageId?: string;
}

@Injectable()
export class MessagesService {
  constructor(
    private readonly messagesRepository: MessagesRepository,
    private readonly conversationsService: ConversationsService,
    private readonly conversationsRepository: ConversationsRepository,
    private readonly attachmentsService: AttachmentsService,
    private readonly usersService: UsersService,
    private readonly notificationsService: NotificationsService,
    @Inject(REALTIME_BROADCASTER) private readonly broadcaster: RealtimeBroadcaster,
  ) {}

  async send(input: SendMessageInput): Promise<MessageDto> {
    const { conversationId, senderId, clientMsgId } = input;
    await this.conversationsService.assertMember(conversationId, senderId);

    let kind: 'text' | 'image' | 'file' | 'audio' | 'video' = 'text';

    if (input.attachmentId) {
      const attachment = await this.attachmentsService.findById(input.attachmentId);
      if (!attachment) {
        throw new NotFoundException('Attachment not found');
      }
      // Two checks, not one: the attachment must belong to THIS
      // conversation (can't reference a file uploaded somewhere else) AND
      // to THIS sender (can't attach someone else's upload to your own
      // message) — either alone would leave a real hole.
      if (attachment.conversation_id !== conversationId) {
        throw new BadRequestException('Attachment does not belong to this conversation');
      }
      if (attachment.uploader_id !== senderId) {
        throw new ForbiddenException('Cannot send a message with another user\'s attachment');
      }
      kind = attachment.kind as 'image' | 'file' | 'audio' | 'video';
    }

    const row = await this.messagesRepository.createIdempotent({
      conversationId,
      senderId,
      clientMsgId,
      kind,
      body: input.body ?? null,
      attachmentId: input.attachmentId ?? null,
      forwardedFromMessageId: input.forwardedFromMessageId ?? null,
    });
    const dto = toDto(row);

    const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(
      conversationId,
      senderId,
    );
    this.broadcaster.broadcastToUsers(otherMemberIds, { type: 'message.created', message: dto });

    // Push notifications (Phase 6) — fire-and-forget, and deliberately
    // AFTER the WebSocket broadcast above: NotificationsService itself
    // skips any recipient with a live connection, so it must see the
    // post-broadcast world, not race it. Never awaited into a failure
    // path for the send() call itself — see NotificationsService's own
    // per-recipient error handling for why this is safe to call directly
    // rather than wrapping in a try/catch here too.
    const conversation = await this.conversationsRepository.findById(conversationId);
    const sender = await this.usersService.findById(senderId);
    void this.notificationsService.notifyNewMessage({
      recipientUserIds: otherMemberIds,
      conversationId,
      conversationTitle: conversation?.title ?? null,
      senderDisplayName: sender?.display_name ?? sender?.phone_e164 ?? 'Someone',
      messageKind: dto.kind,
      messageBody: dto.body,
    });

    return dto;
  }

  async list(conversationId: string, requestingUserId: string, after: number, limit: number) {
    await this.conversationsService.assertMember(conversationId, requestingUserId);
    const rows = await this.messagesRepository.listForConversation(conversationId, after, limit);
    const reactionsByMessage = await this.messagesRepository.listReactionsForMessages(
      rows.map((r) => r.id),
    );
    return rows.map((row) => toDto(row, reactionsByMessage.get(row.id) ?? []));
  }

  /**
   * Text-only — editing an image/file/audio/video message's caption isn't
   * supported (there's no caption field on those messages yet in this
   * schema), so this simply rejects rather than silently no-op'ing.
   */
  async edit(conversationId: string, messageId: string, requestingUserId: string, body: string): Promise<MessageDto> {
    await this.conversationsService.assertMember(conversationId, requestingUserId);

    const existing = await this.messagesRepository.findById(messageId);
    if (!existing || existing.conversation_id !== conversationId) {
      throw new NotFoundException('Message not found');
    }
    if (existing.sender_id !== requestingUserId) {
      throw new ForbiddenException('Cannot edit another user\'s message');
    }
    if (existing.kind !== 'text') {
      throw new BadRequestException('Only text messages can be edited');
    }

    const updated = await this.messagesRepository.editBody(messageId, requestingUserId, body);
    if (!updated) {
      // Ownership/kind/not-deleted were all already checked above, so
      // reaching here means the message was deleted in the moment
      // between that check and the UPDATE — a genuine (if rare) race,
      // not a bug in the checks themselves.
      throw new NotFoundException('Message not found');
    }

    const reactions = await this.messagesRepository.listReactionsForMessage(messageId);
    const dto = toDto(updated, reactions);

    const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(conversationId, requestingUserId);
    this.broadcaster.broadcastToUsers(otherMemberIds, { type: 'message.updated', message: dto });

    return dto;
  }

  async delete(conversationId: string, messageId: string, requestingUserId: string): Promise<void> {
    await this.conversationsService.assertMember(conversationId, requestingUserId);

    const existing = await this.messagesRepository.findById(messageId);
    if (!existing || existing.conversation_id !== conversationId) {
      throw new NotFoundException('Message not found');
    }
    if (existing.sender_id !== requestingUserId) {
      throw new ForbiddenException('Cannot delete another user\'s message');
    }

    const updated = await this.messagesRepository.softDelete(messageId, requestingUserId);
    if (!updated) {
      throw new NotFoundException('Message not found');
    }

    const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(conversationId, requestingUserId);
    this.broadcaster.broadcastToUsers(otherMemberIds, {
      type: 'message.deleted',
      conversationId,
      messageId,
      deletedAt: updated.deleted_at!.toISOString(),
    });
  }

  async react(conversationId: string, messageId: string, requestingUserId: string, emoji: string): Promise<ReactionSummary[]> {
    await this.conversationsService.assertMember(conversationId, requestingUserId);

    const existing = await this.messagesRepository.findById(messageId);
    if (!existing || existing.conversation_id !== conversationId || existing.deleted_at) {
      throw new NotFoundException('Message not found');
    }

    await this.messagesRepository.setReaction(messageId, requestingUserId, emoji);
    return this.broadcastReactions(conversationId, messageId, requestingUserId);
  }

  async unreact(conversationId: string, messageId: string, requestingUserId: string): Promise<ReactionSummary[]> {
    await this.conversationsService.assertMember(conversationId, requestingUserId);
    await this.messagesRepository.clearReaction(messageId, requestingUserId);
    return this.broadcastReactions(conversationId, messageId, requestingUserId);
  }

  private async broadcastReactions(
    conversationId: string,
    messageId: string,
    requestingUserId: string,
  ): Promise<ReactionSummary[]> {
    const reactions = await this.messagesRepository.listReactionsForMessage(messageId);
    const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(conversationId, requestingUserId);
    this.broadcaster.broadcastToUsers(otherMemberIds, {
      type: 'reaction.updated',
      conversationId,
      messageId,
      reactions,
    });
    return reactions;
  }

  /**
   * Only text messages can be forwarded right now. A media message's
   * attachment row is scoped to the conversation it was uploaded into
   * (see send()'s "attachment does not belong to this conversation"
   * check) — genuinely copying the underlying file into a second
   * conversation is real Phase 5+ work (storage-object duplication or a
   * shared-attachment model), not something to fake by re-pointing the
   * existing attachment row at a conversation it wasn't authorized for.
   * Rejecting clearly here beats silently producing a broken attachment
   * reference.
   */
  async forward(
    targetConversationId: string,
    sourceMessageId: string,
    requestingUserId: string,
    clientMsgId: string,
  ): Promise<MessageDto> {
    const source = await this.messagesRepository.findById(sourceMessageId);
    if (!source || source.deleted_at) {
      throw new NotFoundException('Message not found');
    }
    await this.conversationsService.assertMember(source.conversation_id, requestingUserId);
    await this.conversationsService.assertMember(targetConversationId, requestingUserId);

    if (source.kind !== 'text') {
      throw new BadRequestException('Forwarding media messages isn\'t supported yet');
    }

    return this.send({
      conversationId: targetConversationId,
      senderId: requestingUserId,
      clientMsgId,
      body: source.body ?? undefined,
      forwardedFromMessageId: source.id,
    });
  }

  async markRead(conversationId: string, userId: string, lastReadSequence: number): Promise<void> {
    await this.conversationsService.assertMember(conversationId, userId);
    await this.messagesRepository.upsertReadReceipt(conversationId, userId, lastReadSequence);

    const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(
      conversationId,
      userId,
    );
    this.broadcaster.broadcastToUsers(otherMemberIds, {
      type: 'message.read',
      conversationId,
      userId,
      lastReadSequence,
    });
  }
}
