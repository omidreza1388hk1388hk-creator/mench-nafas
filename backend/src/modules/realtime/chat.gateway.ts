import { Logger } from '@nestjs/common';
import {
  OnGatewayConnection,
  OnGatewayDisconnect,
  WebSocketGateway,
} from '@nestjs/websockets';
import { IncomingMessage } from 'http';
import { URL } from 'url';
import WebSocket from 'ws';
import { TokenService } from '../sessions/token.service';
import { ConversationsRepository } from '../conversations/conversations.repository';
import { CallsRepository } from '../calls/calls.repository';
import { RealtimeBroadcaster } from './realtime-broadcaster.interface';
import { ClientToServerEvent, ServerToClientEvent } from './realtime-events';

interface AuthenticatedSocket extends WebSocket {
  userId?: string;
}

/**
 * Raw-'ws' gateway (not socket.io — see main.ts's useWebSocketAdapter).
 * Deliberately does NOT use @SubscribeMessage()/Nest's built-in message
 * routing: that routing assumes a socket.io-style {event, data} envelope
 * that isn't part of this project's wire contract (see realtime-events.ts,
 * which uses a flat `type` field on both directions for symmetry with the
 * REST API's JSON shapes). Incoming messages are parsed and dispatched
 * manually instead, so the wire format is exactly what's documented in
 * realtime-events.ts and nothing implicit from the framework.
 *
 * Path is /ws, NOT prefixed with /api/v1 — app.setGlobalPrefix() only
 * applies to the HTTP router, not to the raw WebSocket upgrade handled by
 * WsAdapter. Documented in docs/ARCHITECTURE.md.
 */
@WebSocketGateway({ path: '/ws' })
export class ChatGateway implements OnGatewayConnection, OnGatewayDisconnect, RealtimeBroadcaster {
  private readonly logger = new Logger('ChatGateway');
  private readonly connectionsByUserId = new Map<string, Set<AuthenticatedSocket>>();

  constructor(
    private readonly tokenService: TokenService,
    private readonly conversationsRepository: ConversationsRepository,
    private readonly callsRepository: CallsRepository,
  ) {}

  handleConnection(client: AuthenticatedSocket, request: IncomingMessage): void {
    const token = this.extractToken(request);
    if (!token) {
      client.close(4001, 'Missing token');
      return;
    }

    let userId: string;
    try {
      userId = this.tokenService.verifyAccessToken(token).sub;
    } catch {
      client.close(4001, 'Invalid or expired token');
      return;
    }

    client.userId = userId;
    const existing = this.connectionsByUserId.get(userId) ?? new Set<AuthenticatedSocket>();
    existing.add(client);
    this.connectionsByUserId.set(userId, existing);

    client.on('message', (raw: WebSocket.RawData) => {
      this.handleIncomingMessage(client, raw).catch((err) => {
        this.logger.error('Failed to handle incoming WS message', err as Error);
      });
    });
  }

  handleDisconnect(client: AuthenticatedSocket): void {
    if (!client.userId) return;
    const sockets = this.connectionsByUserId.get(client.userId);
    sockets?.delete(client);
    if (sockets && sockets.size === 0) {
      this.connectionsByUserId.delete(client.userId);
    }
  }

  broadcastToUsers(userIds: string[], event: ServerToClientEvent): void {
    const payload = JSON.stringify(event);
    for (const userId of userIds) {
      const sockets = this.connectionsByUserId.get(userId);
      if (!sockets) continue;
      for (const socket of sockets) {
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(payload);
        }
      }
    }
  }

  isUserConnected(userId: string): boolean {
    const sockets = this.connectionsByUserId.get(userId);
    if (!sockets) return false;
    for (const socket of sockets) {
      if (socket.readyState === WebSocket.OPEN) return true;
    }
    return false;
  }

  private async handleIncomingMessage(client: AuthenticatedSocket, raw: WebSocket.RawData): Promise<void> {
    if (!client.userId) return;

    let event: ClientToServerEvent;
    try {
      event = JSON.parse(raw.toString());
    } catch {
      return; // malformed frame — silently ignored, not a server error
    }

    if (event.type === 'typing.started' || event.type === 'typing.stopped') {
      // Authorization on every inbound event, not just REST endpoints:
      // only relay typing state to members of a conversation this user
      // actually belongs to.
      const isMember = await this.conversationsRepository.isMember(event.conversationId, client.userId);
      if (!isMember) return;

      const otherMemberIds = await this.conversationsRepository.listOtherMemberIds(
        event.conversationId,
        client.userId,
      );
      this.broadcastToUsers(otherMemberIds, {
        type: event.type,
        conversationId: event.conversationId,
        userId: client.userId,
      });
      return;
    }

    if (
      event.type === 'call.offer' ||
      event.type === 'call.answer' ||
      event.type === 'call.ice-candidate' ||
      event.type === 'call.hangup'
    ) {
      await this.relayCallSignal(client.userId, event);
      return;
    }
  }

  /**
   * Every call signaling frame is authorized the same way: the sender
   * must be one of this call's two participants (CallsRepository, not
   * ConversationsRepository — a call's callee is fixed at creation time
   * and doesn't change even if conversation membership somehow did), and
   * it is relayed to exactly the other participant, never broadcast more
   * widely. None of these frames touch the database — see the doc
   * comment on CallIncomingSummary in realtime-events.ts for why.
   */
  private async relayCallSignal(
    fromUserId: string,
    event: Extract<
      ClientToServerEvent,
      { type: 'call.offer' | 'call.answer' | 'call.ice-candidate' | 'call.hangup' }
    >,
  ): Promise<void> {
    const isParticipant = await this.callsRepository.isParticipant(event.callId, fromUserId);
    if (!isParticipant) return;

    const otherPartyId = await this.callsRepository.getOtherParticipantId(event.callId, fromUserId);
    if (!otherPartyId) return;

    let outgoing: ServerToClientEvent;
    switch (event.type) {
      case 'call.offer':
        outgoing = { type: 'call.offer', callId: event.callId, from: fromUserId, sdp: event.sdp };
        break;
      case 'call.answer':
        outgoing = { type: 'call.answer', callId: event.callId, from: fromUserId, sdp: event.sdp };
        break;
      case 'call.ice-candidate':
        outgoing = {
          type: 'call.ice-candidate',
          callId: event.callId,
          from: fromUserId,
          candidate: event.candidate,
          sdpMid: event.sdpMid,
          sdpMLineIndex: event.sdpMLineIndex,
        };
        break;
      case 'call.hangup':
        outgoing = { type: 'call.hangup', callId: event.callId, from: fromUserId };
        break;
    }

    this.broadcastToUsers([otherPartyId], outgoing);
  }

  private extractToken(request: IncomingMessage): string | null {
    if (!request.url) return null;
    // request.url is path+query only (no host); URL requires an absolute
    // base, which is never actually used for resolution here.
    const url = new URL(request.url, 'http://internal');
    return url.searchParams.get('token');
  }
}
