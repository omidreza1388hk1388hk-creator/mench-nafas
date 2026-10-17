import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { MessagesService } from './messages.service';
import { MessagesRepository, MessageRow } from './messages.repository';
import { ConversationsService } from '../conversations/conversations.service';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { AttachmentsService } from '../attachments/attachments.service';
import { UsersService } from '../users/users.service';
import { NotificationsService } from '../notifications/notifications.service';
import { AccountService } from '../account/account.service';
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
    attachment_waveform: null,
    attachment_thumbnail_storage_key: null,
    sequence: '42', // pg returns BIGSERIAL as a string
    created_at: new Date('2026-01-01T00:00:00.000Z'),
    edited_at: null,
    deleted_at: null,
    forwarded_from_message_id: null,
    reply_to_message_id: null,
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
      listMutedMemberIds: jest.fn().mockResolvedValue([]),
      findById: jest.fn(),
      isGroup: jest.fn().mockResolvedValue(true),
      getSendContext: jest.fn().mockResolvedValue(null), // null / non-group = no channel or slow-mode rules
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
      // Phase 1C: block check dependency; nobody is blocked in these tests.
      { assertNotBlocked: jest.fn().mockResolvedValue(undefined) } as unknown as AccountService,
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
      attachment_waveform: null,
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
      waveform: null,
      hasThumbnail: true,
      blurHash: null,
    });
  });

  it('a plain member cannot post in an admins-only channel, but an admin can', async () => {
    conversationsRepo.listOtherMemberIds.mockResolvedValue([otherMemberId]);
    messagesRepo.createIdempotent.mockResolvedValue(sampleRow);
    conversationsRepo.getSendContext.mockResolvedValue({ kind: 'group', role: 'member', sendMessages: 'admins', slowModeSeconds: 0, lastSentAt: null });
    await expect(service.send({ conversationId, senderId, clientMsgId: 'c-1', body: 'hi' })).rejects.toBeInstanceOf(ForbiddenException);
    expect(messagesRepo.createIdempotent).not.toHaveBeenCalled();
    conversationsRepo.getSendContext.mockResolvedValue({ kind: 'group', role: 'admin', sendMessages: 'admins', slowModeSeconds: 0, lastSentAt: null });
    await expect(service.send({ conversationId, senderId, clientMsgId: 'c-2', body: 'hi' })).resolves.toBeDefined();
  });

  it('slow mode answers 429 with the seconds to wait, and exempts admins', async () => {
    conversationsRepo.listOtherMemberIds.mockResolvedValue([otherMemberId]);
    messagesRepo.createIdempotent.mockResolvedValue(sampleRow);
    conversationsRepo.getSendContext.mockResolvedValue({ kind: 'group', role: 'member', sendMessages: 'all', slowModeSeconds: 60, lastSentAt: new Date(Date.now() - 10_000) });
    await expect(service.send({ conversationId, senderId, clientMsgId: 'c-3', body: 'hi' })).rejects.toMatchObject({ status: 429 });
    conversationsRepo.getSendContext.mockResolvedValue({ kind: 'group', role: 'admin', sendMessages: 'all', slowModeSeconds: 60, lastSentAt: new Date(Date.now() - 10_000) });
    await expect(service.send({ conversationId, senderId, clientMsgId: 'c-4', body: 'hi' })).resolves.toBeDefined();
  });

  it('a silent message is delivered live but sends no push notification', async () => {
    messagesRepo.createIdempotent.mockResolvedValue(sampleRow);
    conversationsRepo.listOtherMemberIds.mockResolvedValue([otherMemberId]);
    await service.send({ conversationId, senderId, clientMsgId: 'client-s', body: 'shh', silent: true });
    expect(broadcaster.broadcastToUsers).toHaveBeenCalled();
    expect(notificationsService.notifyNewMessage).not.toHaveBeenCalled();
  });

  it('passes an audio attachment waveform through to the message DTO', async () => {
    const audioRow: MessageRow = {
      ...sampleRow,
      kind: 'audio',
      body: null,
      attachment_id: 'att-2',
      attachment_kind: 'audio',
      attachment_mime_type: 'audio/mp4',
      attachment_original_filename: 'voice-message.m4a',
      attachment_size_bytes: '4096',
      attachment_duration_ms: 4200,
      attachment_waveform: 'AQIDBAUGBwg=',
    };
    attachmentsService.findById.mockResolvedValue({
      id: 'att-2',
      conversation_id: conversationId,
      uploader_id: senderId,
      kind: 'audio',
    } as any);
    messagesRepo.createIdempotent.mockResolvedValue(audioRow);
    conversationsRepo.listOtherMemberIds.mockResolvedValue([otherMemberId]);

    const dto = await service.send({ conversationId, senderId, clientMsgId: 'client-2', attachmentId: 'att-2' });

    expect(dto.attachment?.waveform).toBe('AQIDBAUGBwg=');
    expect(dto.attachment?.durationMs).toBe(4200);
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

describe('MessagesService.seenBy', () => {
  const sender = 'user-sender';
  const build = (message: any) => {
    const repo = {
      findById: jest.fn().mockResolvedValue(message),
      listSeenBy: jest.fn().mockResolvedValue([
        { user_id: 'u2', display_name: 'Sara', phone_e164: '+98900', read_at: new Date('2026-10-06T10:00:00Z') },
      ]),
    };
    const conv = { assertMember: jest.fn().mockResolvedValue(undefined) };
    const service = new MessagesService(repo as any, conv as any, {} as any, {} as any, {} as any, {} as any, {} as any, {} as any);
    return { service, repo };
  };

  it('returns readers to the sender', async () => {
    const { service, repo } = build({ id: 'm1', conversation_id: 'c1', sender_id: sender, sequence: '7' });
    await expect(service.seenBy('c1', sender, 'm1')).resolves.toEqual([
      { userId: 'u2', displayName: 'Sara', phoneE164: '+98900', readAt: '2026-10-06T10:00:00.000Z' },
    ]);
    expect(repo.listSeenBy).toHaveBeenCalledWith('c1', 7, sender);
  });

  it('refuses anyone but the sender and unknown / foreign messages', async () => {
    const other = build({ id: 'm1', conversation_id: 'c1', sender_id: sender, sequence: '7' });
    await expect(other.service.seenBy('c1', 'someone-else', 'm1')).rejects.toBeInstanceOf(ForbiddenException);
    const missing = build(null);
    await expect(missing.service.seenBy('c1', sender, 'm1')).rejects.toBeInstanceOf(NotFoundException);
    const foreign = build({ id: 'm1', conversation_id: 'other-conv', sender_id: sender, sequence: '7' });
    await expect(foreign.service.seenBy('c1', sender, 'm1')).rejects.toBeInstanceOf(NotFoundException);
  });
});

describe('MessagesService.editHistory', () => {
  const build = (message: any) => {
    const repo = {
      findById: jest.fn().mockResolvedValue(message),
      listEdits: jest.fn().mockResolvedValue([{ old_body: 'first', edited_at: new Date('2026-10-06T10:00:00Z') }]),
    };
    const conv = { assertMember: jest.fn().mockResolvedValue(undefined) };
    const service = new MessagesService(repo as any, conv as any, {} as any, {} as any, {} as any, {} as any, {} as any, {} as any);
    return { service, repo };
  };

  it('returns earlier versions to any member', async () => {
    const { service } = build({ id: 'm1', conversation_id: 'c1', sender_id: 'a', deleted_at: null });
    await expect(service.editHistory('c1', 'b', 'm1')).resolves.toEqual([
      { body: 'first', replacedAt: '2026-10-06T10:00:00.000Z' },
    ]);
  });

  it('returns nothing for a deleted message and 404s a foreign or unknown one', async () => {
    const deleted = build({ id: 'm1', conversation_id: 'c1', sender_id: 'a', deleted_at: new Date() });
    await expect(deleted.service.editHistory('c1', 'b', 'm1')).resolves.toEqual([]);
    expect(deleted.repo.listEdits).not.toHaveBeenCalled();
    const missing = build(null);
    await expect(missing.service.editHistory('c1', 'b', 'm1')).rejects.toBeInstanceOf(NotFoundException);
    const foreign = build({ id: 'm1', conversation_id: 'zzz', sender_id: 'a', deleted_at: null });
    await expect(foreign.service.editHistory('c1', 'b', 'm1')).rejects.toBeInstanceOf(NotFoundException);
  });
});
