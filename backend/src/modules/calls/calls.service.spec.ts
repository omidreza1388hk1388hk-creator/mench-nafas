import { BadRequestException, NotFoundException } from '@nestjs/common';
import { CallsService } from './calls.service';
import { CallsRepository, CallRow } from './calls.repository';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { UsersService } from '../users/users.service';
import { IceServersService } from './ice-servers.service';
import { RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';

describe('CallsService', () => {
  let service: CallsService;
  let calls: jest.Mocked<CallsRepository>;
  let conversations: jest.Mocked<ConversationsRepository>;
  let users: jest.Mocked<UsersService>;
  let iceServers: jest.Mocked<IceServersService>;
  let broadcaster: jest.Mocked<RealtimeBroadcaster>;

  const callerId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
  const calleeId = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
  const strangerId = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
  const conversationId = 'conv-1';
  const callId = 'call-1';

  const ringingCall: CallRow = {
    id: callId,
    conversation_id: conversationId,
    caller_id: callerId,
    callee_id: calleeId,
    call_type: 'voice',
    status: 'ringing',
    end_reason: null,
    started_at: new Date('2026-01-01T00:00:00Z'),
    answered_at: null,
    ended_at: null,
  };

  beforeEach(() => {
    calls = {
      create: jest.fn(),
      findById: jest.fn(),
      isParticipant: jest.fn(),
      getOtherParticipantId: jest.fn(),
      markAccepted: jest.fn(),
      markDeclined: jest.fn(),
      markEnded: jest.fn(),
      listHistoryForUser: jest.fn(),
    } as unknown as jest.Mocked<CallsRepository>;

    conversations = {
      isMember: jest.fn(),
      isGroup: jest.fn(),
      getOtherMember: jest.fn(),
    } as unknown as jest.Mocked<ConversationsRepository>;

    users = { findById: jest.fn() } as unknown as jest.Mocked<UsersService>;
    iceServers = { build: jest.fn().mockReturnValue([]) } as unknown as jest.Mocked<IceServersService>;
    broadcaster = { broadcastToUsers: jest.fn(), isUserConnected: jest.fn() } as unknown as jest.Mocked<RealtimeBroadcaster>;

    service = new CallsService(calls, conversations, users, iceServers, broadcaster);
  });

  describe('initiate', () => {
    it('rejects when the caller is not a member of the conversation', async () => {
      conversations.isMember.mockResolvedValue(false);
      await expect(service.initiate(callerId, conversationId, 'voice')).rejects.toBeInstanceOf(NotFoundException);
      expect(calls.create).not.toHaveBeenCalled();
    });

    it('rejects group conversations — 1:1 only for this phase', async () => {
      conversations.isMember.mockResolvedValue(true);
      conversations.isGroup.mockResolvedValue(true);
      await expect(service.initiate(callerId, conversationId, 'voice')).rejects.toBeInstanceOf(BadRequestException);
      expect(calls.create).not.toHaveBeenCalled();
    });

    it('creates the call and notifies only the callee', async () => {
      conversations.isMember.mockResolvedValue(true);
      conversations.isGroup.mockResolvedValue(false);
      conversations.getOtherMember.mockResolvedValue({
        id: calleeId,
        phone_e164: '+15551234567',
        display_name: 'Callee',
      });
      calls.create.mockResolvedValue(ringingCall);
      users.findById.mockResolvedValue({ id: callerId, display_name: 'Caller', phone_e164: '+15550000000' } as any);

      const result = await service.initiate(callerId, conversationId, 'voice');

      expect(result.id).toBe(callId);
      expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
        [calleeId],
        expect.objectContaining({ type: 'call.incoming' }),
      );
    });
  });

  describe('accept', () => {
    it('rejects a non-participant', async () => {
      calls.findById.mockResolvedValue(ringingCall);
      await expect(service.accept(callId, strangerId)).rejects.toBeInstanceOf(NotFoundException);
    });

    it('rejects the caller trying to accept their own call', async () => {
      calls.findById.mockResolvedValue(ringingCall);
      await expect(service.accept(callId, callerId)).rejects.toBeInstanceOf(BadRequestException);
      expect(calls.markAccepted).not.toHaveBeenCalled();
    });

    it('accepts and notifies the caller', async () => {
      calls.findById.mockResolvedValue(ringingCall);
      calls.markAccepted.mockResolvedValue({ ...ringingCall, status: 'active', answered_at: new Date() });

      await service.accept(callId, calleeId);

      expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
        [callerId],
        expect.objectContaining({ type: 'call.accepted', by: calleeId }),
      );
    });
  });

  describe('end', () => {
    it('is a no-op (not an error) if the call already ended', async () => {
      calls.findById.mockResolvedValue({ ...ringingCall, status: 'ended' });
      calls.markEnded.mockResolvedValue(null);

      await service.end(callId, callerId, 'user_hangup');

      expect(broadcaster.broadcastToUsers).not.toHaveBeenCalled();
    });

    it('notifies whichever party did not hang up', async () => {
      calls.findById.mockResolvedValue({ ...ringingCall, status: 'active', answered_at: new Date() });
      calls.markEnded.mockResolvedValue({
        ...ringingCall,
        status: 'ended',
        answered_at: new Date(),
        ended_at: new Date(),
        end_reason: 'user_hangup',
      });

      await service.end(callId, calleeId, 'user_hangup');

      expect(broadcaster.broadcastToUsers).toHaveBeenCalledWith(
        [callerId],
        expect.objectContaining({ type: 'call.ended', by: calleeId, status: 'ended' }),
      );
    });
  });
});
