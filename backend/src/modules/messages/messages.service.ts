import {
  BadRequestException,
  ForbiddenException,
  HttpException,
  HttpStatus,
  Inject,
  Injectable,
  NotFoundException,
  Optional,
} from '@nestjs/common';
import { decideSend, toRole } from '../conversations/group-policy';
import { MessagesRepository, MessageRow } from './messages.repository';
import { ConversationsService } from '../conversations/conversations.service';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { AttachmentsService } from '../attachments/attachments.service';
import {
  REALTIME_BROADCASTER,
  RealtimeBroadcaster,
} from '../realtime/realtime-broadcaster.interface';
import { MessageDto, ReactionSummary } from '../realtime/realtime-events';
import { UsersService } from '../users/users.service';
import { NotificationsService } from '../notifications/notifications.service';
import { AccountService } from '../account/account.service';
import { PrivacyService } from '../privacy/privacy.service';

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
          waveform: row.attachment_waveform ?? null,
          hasThumbnail: row.attachment_thumbnail_storage_key !== null,
          blurHash: row.attachment_blur_hash ?? null,
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
    replyToMessageId: row.reply_to_message_id,
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
  /** The message this one is quoting/replying to, within the same conversation — validated in send() below, not just trusted from the client. */
  replyToMessageId?: string;
  /** Phase 2A: skip the push notification for this message (it is still delivered live / on next sync). */
  silent?: boolean;
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
    private readonly accountService: AccountService,
    @Inject(REALTIME_BROADCASTER) private readonly broadcaster: RealtimeBroadcaster,
    @Optional() private readonly privacy?: PrivacyService,
  ) {}

  /** Phase 2A: channel (admins-only) and slow-mode rules for groups. Direct chats have no such rules. */
  private async assertMaySend(
    conversationId: string,
    senderId: string,
    clientMsgId: string,
  ): Promise<void> {
    const context = await this.conversationsRepository.getSendContext(
      conversationId,
      senderId,
      clientMsgId,
    );
    if (!context || context.kind !== 'group') return;
    const decision = decideSend(
      { sendMessages: context.sendMessages, slowModeSeconds: context.slowModeSeconds },
      toRole(context.role),
      context.lastSentAt ? context.lastSentAt.getTime() : null,
      Date.now(),
    );
    if (decision.ok) return;
    if (decision.reason === 'admins_only')
      throw new ForbiddenException('Only admins can post in this channel');
    throw new HttpException(
      {
        statusCode: 429,
        error: 'Too Many Requests',
        message: `Slow mode is on — wait ${decision.retryAfterSeconds}s`,
        retryAfterSeconds: decision.retryAfterSeconds,
      },
      HttpStatus.TOO_MANY_REQUESTS,
    );
  }

  async send(input: SendMessageInput): Promise<MessageDto> {
    const { conversationId, senderId, clientMsgId } = input;
    await this.conversationsService.assertMember(conversationId, senderId);
    await this.assertMaySend(conversationId, senderId, clientMsgId);

    // Phase 1C: a person you blocked cannot message you in a direct chat.
    await this.accountService.assertNotBlocked(conversationId, senderId, input.body ?? null);

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
        throw new ForbiddenException("Cannot send a message with another user's attachment");
      }
      kind = attachment.kind as 'image' | 'file' | 'audio' | 'video';
    }

    if (input.replyToMessageId) {
      const replyTarget = await this.messagesRepository.findById(input.replyToMessageId);
      // Same shape as the attachment checks above: must exist AND belong
      // to this conversation. Deliberately not rejecting a reply to an
      // already-deleted message — the client still has (and shows) the
      // deleted-message placeholder at that spot in history, so quoting
      // it is harmless, unlike quoting a message from a conversation the
      // sender was never a member of.
      if (!replyTarget || replyTarget.conversation_id !== conversationId) {
        throw new BadRequestException(
          'Message being replied to was not found in this conversation',
        );
      }
    }

    const row = await this.messagesRepository.createIdempotent({
      conversationId,
      senderId,
      clientMsgId,
      kind,
      body: input.body ?? null,
      attachmentId: input.attachmentId ?? null,
      forwardedFromMessageId: input.forwardedFromMessageId ?? null,
      replyToMessageId: input.replyToMessageId ?? null,
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
    // Phase 8: a member who muted this conversation still gets the message
    // (live over the socket, or on next sync) — only the push is skipped.
    const mutedIds = new Set(
      (await this.conversationsRepository.listMutedMemberIds(conversationId)) ?? [],
    );
    if (input.silent === true) return dto; // Phase 2A: "send without sound" — no push at all
    void this.notificationsService.notifyNewMessage({
      recipientUserIds: otherMemberIds.filter((id) => !mutedIds.has(id)),
      conversationId,
      conversationTitle: conversation?.title ?? null,
      senderDisplayName: sender?.display_name ?? sender?.phone_e164 ?? 'Someone',
      messageKind: dto.kind,
      messageBody: dto.body,
    });

    return dto;
  }

  /** Who has seen one of my messages in a group. Only the sender may ask (members must not see each other's read state for other people's messages). */
  async seenBy(conversationId: string, requesterId: string, messageId: string) {
    await this.conversationsService.assertMember(conversationId, requesterId);
    const message = await this.messagesRepository.findById(messageId);
    if (!message || message.conversation_id !== conversationId)
      throw new NotFoundException('Message not found');
    if (message.sender_id !== requesterId)
      throw new ForbiddenException('Only the sender can see who read a message');
    const rows = await this.messagesRepository.listSeenBy(
      conversationId,
      Number(message.sequence),
      requesterId,
    );
    return rows.map((r) => ({
      userId: r.user_id,
      displayName: r.display_name,
      phoneE164: r.phone_e164,
      readAt: r.read_at.toISOString(),
    }));
  }

  /** Earlier versions of an edited message; any member of the conversation may read them. */
  async editHistory(conversationId: string, requesterId: string, messageId: string) {
    await this.conversationsService.assertMember(conversationId, requesterId);
    const message = await this.messagesRepository.findById(messageId);
    if (!message || message.conversation_id !== conversationId)
      throw new NotFoundException('Message not found');
    if (message.deleted_at) return [];
    const rows = await this.messagesRepository.listEdits(messageId);
    return rows.map((r) => ({ body: r.old_body ?? '', replacedAt: r.edited_at.toISOString() }));
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
  async edit(
    conversationId: string,
    messageId: string,
    requestingUserId: string,
    body: string,
  ): Promise<MessageDto> {
    await this.conversationsService.assertMember(conversationId, requestingUserId);

    const existing = await this.messagesRepository.findById(messageId);
    if (!existing || existing.conversation_id !== conversationId) {
      throw new NotFoundException('Message not found');
    }
    if (existing.sender_id !== requestingUserId) {
      throw new ForbiddenException("Cannot edit another user's message");
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

    const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(
      conversationId,
      requestingUserId,
    );
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
      throw new ForbiddenException("Cannot delete another user's message");
    }

    const updated = await this.messagesRepository.softDelete(messageId, requestingUserId);
    if (!updated) {
      throw new NotFoundException('Message not found');
    }

    const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(
      conversationId,
      requestingUserId,
    );
    this.broadcaster.broadcastToUsers(otherMemberIds, {
      type: 'message.deleted',
      conversationId,
      messageId,
      deletedAt: updated.deleted_at!.toISOString(),
    });
  }

  async react(
    conversationId: string,
    messageId: string,
    requestingUserId: string,
    emoji: string,
  ): Promise<ReactionSummary[]> {
    await this.conversationsService.assertMember(conversationId, requestingUserId);

    const existing = await this.messagesRepository.findById(messageId);
    if (!existing || existing.conversation_id !== conversationId || existing.deleted_at) {
      throw new NotFoundException('Message not found');
    }

    await this.messagesRepository.setReaction(messageId, requestingUserId, emoji);
    return this.broadcastReactions(conversationId, messageId, requestingUserId);
  }

  async unreact(
    conversationId: string,
    messageId: string,
    requestingUserId: string,
  ): Promise<ReactionSummary[]> {
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
    const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(
      conversationId,
      requestingUserId,
    );
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
      throw new BadRequestException("Forwarding media messages isn't supported yet");
    }

    return this.send({
      conversationId: targetConversationId,
      senderId: requestingUserId,
      clientMsgId,
      body: source.body ?? undefined,
      forwardedFromMessageId: source.id,
    });
  }

  /**
   * Phase 11: the recipient's device tells us it has RECEIVED messages up to
   * this sequence (before they are read). Relayed live to the other members so
   * the sender's ticks turn into a grey double tick. Not persisted: like
   * message.read, it only matters while the sender is looking.
   */
  async markDelivered(
    conversationId: string,
    userId: string,
    lastDeliveredSequence: number,
  ): Promise<void> {
    await this.conversationsService.assertMember(conversationId, userId);
    const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(
      conversationId,
      userId,
    );
    this.broadcaster.broadcastToUsers(otherMemberIds, {
      type: 'message.delivered',
      conversationId,
      userId,
      lastDeliveredSequence,
    });
  }

  async markRead(conversationId: string, userId: string, lastReadSequence: number): Promise<void> {
    await this.conversationsService.assertMember(conversationId, userId);
    await this.messagesRepository.upsertReadReceipt(conversationId, userId, lastReadSequence);

    // Read receipts off (Phase 4C): the cursor is still stored (unread counts need it) but the
    // other members are not told, so the sender's ticks never turn "read".
    if (this.privacy && !(await this.privacy.sendsReadReceipts(userId))) return;

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
