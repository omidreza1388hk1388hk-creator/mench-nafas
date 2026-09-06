import {
  BadRequestException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { CallsRepository } from './calls.repository';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { UsersService } from '../users/users.service';
import { IceServersService } from './ice-servers.service';
import { REALTIME_BROADCASTER, RealtimeBroadcaster } from '../realtime/realtime-broadcaster.interface';
import {
  CallHistorySummaryDto,
  CallSessionDto,
  toCallHistorySummaryDto,
  toCallSessionDto,
} from './call.dto';

@Injectable()
export class CallsService {
  constructor(
    private readonly callsRepository: CallsRepository,
    private readonly conversationsRepository: ConversationsRepository,
    private readonly usersService: UsersService,
    private readonly iceServersService: IceServersService,
    @Inject(REALTIME_BROADCASTER) private readonly broadcaster: RealtimeBroadcaster,
  ) {}

  /**
   * Group calling is explicitly out of scope for this phase (spec
   * section 8 reserves CallParticipants for it, but no group-call UI or
   * multi-peer SFU/mesh logic exists) — rejected here with a clear 400
   * rather than silently only ringing one arbitrary member, which would
   * be confusing and would look like a bug rather than a documented
   * limitation.
   */
  async initiate(callerId: string, conversationId: string, callType: 'voice' | 'video'): Promise<CallSessionDto> {
    const isMember = await this.conversationsRepository.isMember(conversationId, callerId);
    if (!isMember) {
      throw new NotFoundException('Conversation not found');
    }
    const isGroup = await this.conversationsRepository.isGroup(conversationId);
    if (isGroup) {
      throw new BadRequestException('Group calls are not supported yet — calls are 1:1 only');
    }

    const other = await this.conversationsRepository.getOtherMember(conversationId, callerId);
    if (!other) {
      throw new BadRequestException('This conversation has no other participant to call');
    }

    const call = await this.callsRepository.create(conversationId, callerId, other.id, callType);
    const caller = await this.usersService.findById(callerId);

    this.broadcaster.broadcastToUsers([other.id], {
      type: 'call.incoming',
      call: {
        id: call.id,
        conversationId: call.conversation_id,
        callerId: call.caller_id,
        callType: call.call_type,
        callerDisplayName: caller?.display_name ?? null,
        callerPhoneE164: caller?.phone_e164 ?? '',
      },
    });

    return toCallSessionDto(call, this.iceServersService.build());
  }

  async accept(callId: string, userId: string): Promise<CallSessionDto> {
    const call = await this.requireParticipant(callId, userId);
    if (call.callee_id !== userId) {
      throw new BadRequestException('Only the callee can accept a call');
    }

    const updated = await this.callsRepository.markAccepted(callId);
    if (!updated) {
      throw new BadRequestException('Call is no longer ringing');
    }

    this.broadcaster.broadcastToUsers([updated.caller_id], {
      type: 'call.accepted',
      callId: updated.id,
      by: userId,
    });

    return toCallSessionDto(updated, this.iceServersService.build());
  }

  async decline(callId: string, userId: string): Promise<void> {
    const call = await this.requireParticipant(callId, userId);
    if (call.callee_id !== userId) {
      throw new BadRequestException('Only the callee can decline a call');
    }

    const updated = await this.callsRepository.markDeclined(callId);
    if (!updated) {
      throw new BadRequestException('Call is no longer ringing');
    }

    this.broadcaster.broadcastToUsers([updated.caller_id], {
      type: 'call.declined',
      callId: updated.id,
      by: userId,
    });
  }

  /**
   * Either party may end a call at any point — while it's still ringing
   * (a cancel by the caller, or the callee just doesn't want to see it
   * anymore) or once it's active (a normal hangup). The resulting status
   * ('missed' vs 'ended') is derived server-side; see
   * CallsRepository.markEnded's doc comment.
   */
  async end(callId: string, userId: string, reason: string | undefined): Promise<void> {
    const call = await this.requireParticipant(callId, userId);
    const updated = await this.callsRepository.markEnded(callId, reason ?? null);
    if (!updated) {
      // Already ended (by the other party, or by a near-simultaneous
      // duplicate request from this same client) — not an error, just a
      // no-op. The other party already got their call.ended broadcast
      // from whichever request won.
      return;
    }

    const otherPartyId = call.caller_id === userId ? call.callee_id : call.caller_id;
    this.broadcaster.broadcastToUsers([otherPartyId], {
      type: 'call.ended',
      callId: updated.id,
      by: userId,
      status: updated.status,
      reason: updated.end_reason,
    });
  }

  async get(callId: string, userId: string): Promise<CallSessionDto> {
    const call = await this.requireParticipant(callId, userId);
    return toCallSessionDto(call, this.iceServersService.build());
  }

  async listHistory(userId: string, before: string | undefined, limit: number): Promise<CallHistorySummaryDto[]> {
    const beforeDate = before ? new Date(before) : null;
    const rows = await this.callsRepository.listHistoryForUser(userId, beforeDate, limit);
    return rows.map((row) => toCallHistorySummaryDto(row, userId));
  }

  private async requireParticipant(callId: string, userId: string) {
    const call = await this.callsRepository.findById(callId);
    // 404, not 403 — same reasoning as ConversationsService.assertMember:
    // a non-participant shouldn't be able to distinguish "no such call"
    // from "exists but isn't mine".
    if (!call || (call.caller_id !== userId && call.callee_id !== userId)) {
      throw new NotFoundException('Call not found');
    }
    return call;
  }
}
