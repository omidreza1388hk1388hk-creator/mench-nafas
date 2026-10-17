import { Inject, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';
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
import { isAllowedCallReaction, sanitizeCallChatText } from '../calls/call-extras';

interface AuthenticatedSocket extends WebSocket {
  userId?: string;
  /** Heartbeat flag: set true on every pong, cleared on every server ping. */
  isAlive?: boolean;
}

/** How often the server pings every socket; a socket that hasn't ponged by the next tick is terminated. */
const HEARTBEAT_INTERVAL_MS = 30_000;

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
export class ChatGateway
  implements OnGatewayConnection, OnGatewayDisconnect, RealtimeBroadcaster, OnModuleInit, OnModuleDestroy
{
  private readonly logger = new Logger('ChatGateway');
  private readonly connectionsByUserId = new Map<string, Set<AuthenticatedSocket>>();
  private heartbeatTimer: NodeJS.Timeout | null = null;

  constructor(
    private readonly tokenService: TokenService,
    private readonly conversationsRepository: ConversationsRepository,
    private readonly callsRepository: CallsRepository,
    @Inject(PG_POOL) private readonly pool: Pool,
  ) {}

  /**
   * Server-side heartbeat. Mobile clients routinely vanish without a TCP
   * FIN (network switch, tunnel, doze), leaving a half-open socket that
   * still looks OPEN here — messages "delivered" to it are lost and
   * isUserConnected() lies (which suppresses push). Every tick: any socket
   * that didn't answer the previous ping is terminated (which fires
   * handleDisconnect and cleans the map); the rest get a fresh ping.
   * Uses protocol-level ping/pong frames, so the Android OkHttp client
   * answers automatically — no client change needed.
   */
  onModuleInit(): void {
    this.heartbeatTimer = setInterval(() => this.runHeartbeat(), HEARTBEAT_INTERVAL_MS);
    this.heartbeatTimer.unref?.();
  }

  onModuleDestroy(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private runHeartbeat(): void {
    for (const sockets of this.connectionsByUserId.values()) {
      for (const socket of [...sockets]) {
        if (socket.isAlive === false) {
          socket.terminate();
          continue;
        }
        socket.isAlive = false;
        try {
          socket.ping();
        } catch {
          socket.terminate();
        }
      }
    }
  }

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
    client.isAlive = true;
    client.on('pong', () => {
      client.isAlive = true;
    });
    const existing = this.connectionsByUserId.get(userId) ?? new Set<AuthenticatedSocket>();
    const wasOffline = existing.size === 0;
    existing.add(client);
    this.connectionsByUserId.set(userId, existing);
    this.onSocketOpened(userId, wasOffline).catch((err) => {
      this.logger.error('Presence connect handling failed', err as Error);
    });

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
      this.onLastSocketClosed(client.userId).catch((err) => {
        this.logger.error('Presence disconnect handling failed', err as Error);
      });
    }
  }

  // --- Presence (Phase 8) ---------------------------------------------

  private async listCoMemberIds(userId: string): Promise<string[]> {
    const { rows } = await this.pool.query<{ user_id: string }>(
      `SELECT DISTINCT cm2.user_id
         FROM conversation_members cm1
         JOIN conversation_members cm2 ON cm1.conversation_id = cm2.conversation_id
        WHERE cm1.user_id = $1 AND cm2.user_id <> $1`,
      [userId],
    );
    return rows.map((r) => r.user_id);
  }

  /** "Last seen & online" set to nobody (Phase 4C): the user's presence is never broadcast or snapshotted. */
  private async presenceHidden(userId: string): Promise<boolean> {
    const { rows } = await this.pool.query(
      "SELECT 1 FROM user_privacy WHERE user_id = $1 AND last_seen = 'nobody'",
      [userId],
    );
    return rows.length > 0;
  }

  private async onSocketOpened(userId: string, wasOffline: boolean): Promise<void> {
    const coMembers = await this.listCoMemberIds(userId);
    if (wasOffline && !(await this.presenceHidden(userId))) {
      this.broadcastToUsers(coMembers, { type: 'presence.changed', userId, online: true, lastSeenAt: null });
    }
    if (coMembers.length === 0) return;

    // Snapshot for the connecting client: current state of everyone it chats with.
    const { rows } = await this.pool.query<{ id: string; last_seen_at: Date | null }>(
      `SELECT u.id, u.last_seen_at
         FROM users u
         LEFT JOIN user_privacy p ON p.user_id = u.id
        WHERE u.id = ANY($1::uuid[]) AND COALESCE(p.last_seen, 'everybody') = 'everybody'`,
      [coMembers],
    );
    for (const row of rows) {
      this.broadcastToUsers([userId], {
        type: 'presence.changed',
        userId: row.id,
        online: this.isUserConnected(row.id),
        lastSeenAt: row.last_seen_at ? row.last_seen_at.toISOString() : null,
      });
    }
  }

  private async onLastSocketClosed(userId: string): Promise<void> {
    const { rows } = await this.pool.query<{ last_seen_at: Date }>(
      'UPDATE users SET last_seen_at = now() WHERE id = $1 RETURNING last_seen_at',
      [userId],
    );
    const lastSeenAt = rows[0]?.last_seen_at?.toISOString() ?? new Date().toISOString();
    if (await this.presenceHidden(userId)) return;
    const coMembers = await this.listCoMemberIds(userId);
    this.broadcastToUsers(coMembers, { type: 'presence.changed', userId, online: false, lastSeenAt });
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
      event.type === 'call.hangup' ||
      event.type === 'call.chat' ||
      event.type === 'call.reaction'
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
      { type: 'call.offer' | 'call.answer' | 'call.ice-candidate' | 'call.hangup' | 'call.chat' | 'call.reaction' }
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
      case 'call.chat': {
        const text = sanitizeCallChatText(event.text);
        if (!text) return;
        outgoing = { type: 'call.chat', callId: event.callId, from: fromUserId, text, sentAt: new Date().toISOString() };
        break;
      }
      case 'call.reaction':
        if (!isAllowedCallReaction(event.emoji)) return;
        outgoing = { type: 'call.reaction', callId: event.callId, from: fromUserId, emoji: event.emoji };
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
