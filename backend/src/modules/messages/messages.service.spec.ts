import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { MessagesService } from './messages.service';
import { MessagesRepository, MessageRow } from './messages.repository';
import { ConversationsService } from '../conversations/conversations.service';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { AttachmentsService } from '../attachments/attachments.service';
import { UsersService } from '../users/users.service';
import { NotificationsService } from '../notifications/notifications.service';
import { RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';

describe('MessagesService', () => {
  let service: MessagesService;
  let messagesRepo: jest.Mocked<MessagesRepository>;
  let conversationsService: jest.Mocked<ConversationsService>;
  let conversationsRepo: jest.Mocked<ConversationsRepository>;
  let attachmentsService: jest.Mocked<AttachmentsService>;
  let usersService: jest.Mocked<UsersService>;
  let notificationsService: jest.Mocked<NotificationsService>;
  let broadcaster: jest.Mocked<RealtimeBroadcaster>;

  const conversationId = 'conv-1';
  const senderId = 'user-1';
  const otherMemberId = 'user-2';

  const sampleRow: MessageRow = {
    id: 'msg-1',
    conversation_id: conversationId,
    sender_id: senderId,
    client_msg_id: 'client-1',
    kind: 'text',
    body: 'hello',
    attachment_id: null,
    attachment_kind: null,
    attachment_mime_type: null,
    attachment_original_filename: null,
    attachment_size_bytes: null,
    attachment_width_px: null,
    attachment_height_px: null,
    attachment_duration_ms: null,
    attachment_thumbnail_storage_key: null,
    sequence: '42', // pg returns BIGSERIAL as a string
    created_at: new Date('2026-01-01T00:00:00.000Z'),
    edited_at: null,
    deleted_at: null,
    forwarded_from_message_id: null,
  };

  beforeEach(() => {
    messagesRepo = {
      createIdempotent: jest.fn(),
      listForConversation: jest.fn(),
      upsertReadReceipt: jest.fn(),
      findById: jest.fn(),
      editBody: jest.fn(),
      softDelete: jest.fn(),
      setReaction: jest.fn(),
      clearReaction: jest.fn(),
      listReactionsForMessage: jest.fn().mockResolvedValue([]),
      listReactionsForMessages: jest.fn().mockResolvedValue(new Map()),
    } as unknown as jest.Mocked<MessagesRepository>;

    conversationsService = { assertMember: jest.fn() } as unknown as jest.Mocked<ConversationsService>;
    conversationsRepo = {
      listOtherMemberIds: jest.fn(),
      findById: jest.fn(),
    } as unknown as jest.Mocked<ConversationsRepository>;
    attachmentsService = { findById: jest.fn() } as unknown as jest.Mocked<AttachmentsService>;
    usersService = { findById: jest.fn() } as unknown as jest.Mocked<UsersService>;
    notificationsService = { notifyNewMessage: jest.fn() } as unknown as jest.Mocked<NotificationsService>;
    broadcaster = { broadcastToUsers: jest.fn(), isUserConnected: jest.fn() } as unknown as jest.Mocked<RealtimeBroadcaster>;

    service = new MessagesService(
      messagesRepo,
      conversationsService,
      conversationsRepo,
      attachmentsService,
      usersService,
      notificationsService,
      broadcaster,
    );
  });

  it('checks membership before sending (authorization happens before persistence)', async () => {
    conversationsService.assertMember.mockRejectedValue(new Error('not a member'));

    await expect(
      service.send({ conversationId, senderId, clientMsgId: 'client-1', body: 'hi' }),
    ).rejects.toThrow();
    expect(messagesRepo.createIdempotent).not.toHaveBeenCalled();
  });

  it('converts the BIGSERIAL sequence string to a JSON number in the DTO', async () => {
    messagesRepo.createIdempotent.mockResolvedValue(sampleRow);
    conversationsRepo.listOtherMemberIds.mockResolvedValue([otherMemberId]);

    const dto = await service.send({ conversationId, senderId, clientMsgId: 'client-1', body: 'hello' });

    expect(dto.sequence).toBe(42);
    expect(typeof dto.sequence).toBe('number');
  });

  it('broadcasts message.created to every other member, never to the sender', async () => {
    messagesRepo.createIdempotent.mockResolvedValue(sampleRow);
    conversationsRepo.listOtherMemberIds.mockResolvedValue([otherMemberId]);

    await service.send({ conversationId, senderId, clientMsgId: 'client-1', body: 'hello' });

    expect(conversationsRepo.listOtherMemberIds).toHaveBeenCalledWith(conversationId, senderId);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      [otherMemberId],
      expect.objectContaining({ type: 'message.created' }),
    );
  });

  it('embeds the full attachment summary in the DTO when the message has one', async () => {
    attachmentsService.findById.mockResolvedValue({
      id: 'att-1',
      conversation_id: conversationId,
      uploader_id: senderId,
      kind: 'image',
    } as any);

    const rowWithAttachment: MessageRow = {
      ...sampleRow,
      kind: 'image',
      body: null,
      attachment_id: 'att-1',
      attachment_kind: 'image',
      attachment_mime_type: 'image/jpeg',
      attachment_original_filename: 'photo.jpg',
      attachment_size_bytes: '204800',
      attachment_width_px: 1080,
      attachment_height_px: 1350,
      attachment_duration_ms: null,
      attachment_thumbnail_storage_key: 'thumb-key-1',
    };
    messagesRepo.createIdempotent.mockResolvedValue(rowWithAttachment);
    conversationsRepo.listOtherMemberIds.mockResolvedValue([otherMemberId]);

    const dto = await service.send({ conversationId, senderId, clientMsgId: 'client-1', attachmentId: 'att-1' });

    expect(dto.attachment).toEqual({
      id: 'att-1',
      kind: 'image',
      mimeType: 'image/jpeg',
      originalFilename: 'photo.jpg',
      sizeBytes: 204800,
      widthPx: 1080,
      heightPx: 1350,
      durationMs: null,
      hasThumbnail: true,
    });
  });

  it('rejects an attachmentId that does not exist', async () => {
    attachmentsService.findById.mockResolvedValue(null);

    await expect(
      service.send({ conversationId, senderId, clientMsgId: 'client-1', attachmentId: 'att-missing' }),
    ).rejects.toBeInstanceOf(NotFoundException);
    expect(messagesRepo.createIdempotent).not.toHaveBeenCalled();
  });

  it('rejects an attachment that belongs to a different conversation', async () => {
    attachmentsService.findById.mockResolvedValue({
      id: 'att-1',
      conversation_id: 'some-other-conversation',
      uploader_id: senderId,
      kind: 'image',
    } as any);

    await expect(
      service.send({ conversationId, senderId, clientMsgId: 'client-1', attachmentId: 'att-1' }),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('rejects attaching a file uploaded by a different user', async () => {
    attachmentsService.findById.mockResolvedValue({
      id: 'att-1',
      conversation_id: conversationId,
      uploader_id: 'someone-else',
      kind: 'image',
    } as any);

    await expect(
      service.send({ conversationId, senderId, clientMsgId: 'client-1', attachmentId: 'att-1' }),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('rejects editing another user\'s message', async () => {
    messagesRepo.findById.mockResolvedValue(sampleRow); // sender_id === senderId, not otherMemberId

    await expect(
      service.edit(conversationId, sampleRow.id, otherMemberId, 'nope'),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(messagesRepo.editBody).not.toHaveBeenCalled();
  });

  it('rejects editing a non-text message', async () => {
    messagesRepo.findById.mockResolvedValue({ ...sampleRow, kind: 'image' });

    await expect(
      service.edit(conversationId, sampleRow.id, senderId, 'nope'),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('edit broadcasts message.updated only to other members', async () => {
    messagesRepo.findById.mockResolvedValue(sampleRow);
    messagesRepo.editBody.mockResolvedValue({ ...sampleRow, body: 'edited', edited_at: new Date() });
    conversationsRepo.listOtherMemberIds.mockResolvedValue([otherMemberId]);

    await service.edit(conversationId, sampleRow.id, senderId, 'edited');

    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      [otherMemberId],
      expect.objectContaining({ type: 'message.updated' }),
    );
  });

  it('rejects deleting another user\'s message', async () => {
    messagesRepo.findById.mockResolvedValue(sampleRow);

    await expect(
      service.delete(conversationId, sampleRow.id, otherMemberId),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(messagesRepo.softDelete).not.toHaveBeenCalled();
  });

  it('forward rejects a non-text source message', async () => {
    messagesRepo.findById.mockResolvedValue({ ...sampleRow, kind: 'image' });

    await expect(
      service.forward('target-conv', sampleRow.id, senderId, 'client-2'),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('forward rejects a deleted source message', async () => {
    messagesRepo.findById.mockResolvedValue({ ...sampleRow, deleted_at: new Date() });

    await expect(
      service.forward('target-conv', sampleRow.id, senderId, 'client-2'),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('markRead upserts the receipt and broadcasts message.read to other members', async () => {
    conversationsRepo.listOtherMemberIds.mockResolvedValue([otherMemberId]);

    await service.markRead(conversationId, senderId, 42);

    expect(messagesRepo.upsertReadReceipt).toHaveBeenCalledWith(conversationId, senderId, 42);
    expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
      [otherMemberId],
      { type: 'message.read', conversationId, userId: senderId, lastReadSequence: 42 },
    );
  });
});
