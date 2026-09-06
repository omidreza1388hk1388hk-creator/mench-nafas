import { Inject, Injectable } from '@nestjs/common';
import { Pool, PoolClient } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface ConversationRow {
  id: string;
  kind: string;
  title: string | null;
  created_by: string | null;
  created_at: Date;
  last_message_at: Date | null;
}

export interface MemberRow {
  user_id: string;
  phone_e164: string;
  display_name: string | null;
  role: string;
}

export interface OtherMemberRow {
  id: string;
  phone_e164: string;
  display_name: string | null;
}

@Injectable()
export class ConversationsRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  /**
   * Finds the existing direct conversation between exactly these two users,
   * if one exists. Direct conversations are not user-creatable duplicates —
   * this is what makes createDirect() below idempotent.
   */
  async findDirectBetween(userIdA: string, userIdB: string): Promise<ConversationRow | null> {
    const { rows } = await this.pool.query<ConversationRow>(
      `SELECT c.* FROM conversations c
       JOIN conversation_members m1 ON m1.conversation_id = c.id AND m1.user_id = $1
       JOIN conversation_members m2 ON m2.conversation_id = c.id AND m2.user_id = $2
       WHERE c.kind = 'direct'
       LIMIT 1`,
      [userIdA, userIdB],
    );
    return rows[0] ?? null;
  }

  /**
   * Creates a new direct conversation and both memberships atomically. Does
   * NOT check for an existing one — call findDirectBetween() first (see
   * ConversationsService.createDirect for the check-then-create sequence,
   * itself made race-safe with a unique constraint at the DB level would
   * be a further hardening step; acceptable for Phase 2's traffic level).
   */
  async createDirect(userIdA: string, userIdB: string): Promise<ConversationRow> {
    const client: PoolClient = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const { rows } = await client.query<ConversationRow>(
        `INSERT INTO conversations (kind, created_by) VALUES ('direct', $1) RETURNING *`,
        [userIdA],
      );
      const conversation = rows[0];
      await client.query(
        `INSERT INTO conversation_members (conversation_id, user_id) VALUES ($1, $2), ($1, $3)`,
        [conversation.id, userIdA, userIdB],
      );
      await client.query('COMMIT');
      return conversation;
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async listForUser(userId: string): Promise<ConversationRow[]> {
    const { rows } = await this.pool.query<ConversationRow>(
      `SELECT c.* FROM conversations c
       JOIN conversation_members m ON m.conversation_id = c.id
       WHERE m.user_id = $1
       ORDER BY c.last_message_at DESC NULLS LAST, c.created_at DESC`,
      [userId],
    );
    return rows;
  }

  /**
   * The other participant's basic info for a direct conversation — the
   * Android client needs this to render "who is this conversation with"
   * in the list without an N+1 lookup per conversation. Only meaningful
   * for kind='direct' (exactly one other member); group conversations
   * (Phase 3+) will need a different summary shape entirely, not this
   * method.
   */
  async getOtherMember(conversationId: string, excludingUserId: string): Promise<OtherMemberRow | null> {
    const { rows } = await this.pool.query<OtherMemberRow>(
      `SELECT u.id, u.phone_e164, u.display_name
       FROM conversation_members cm
       JOIN users u ON u.id = cm.user_id
       WHERE cm.conversation_id = $1 AND cm.user_id != $2
       LIMIT 1`,
      [conversationId, excludingUserId],
    );
    return rows[0] ?? null;
  }

  /** Same as getOtherMember, batched for every direct conversation a user is in — used by listForUser's DTO mapping to avoid one query per conversation. */
  async listOtherMembersForUser(userId: string): Promise<Map<string, OtherMemberRow>> {
    const { rows } = await this.pool.query<OtherMemberRow & { conversation_id: string }>(
      `SELECT c.id AS conversation_id, u.id, u.phone_e164, u.display_name
       FROM conversations c
       JOIN conversation_members mine ON mine.conversation_id = c.id AND mine.user_id = $1
       JOIN conversation_members other ON other.conversation_id = c.id AND other.user_id != $1
       JOIN users u ON u.id = other.user_id
       WHERE c.kind = 'direct'`,
      [userId],
    );
    const map = new Map<string, OtherMemberRow>();
    for (const row of rows) {
      map.set(row.conversation_id, { id: row.id, phone_e164: row.phone_e164, display_name: row.display_name });
    }
    return map;
  }

  /** Needed (Phase 6) to build the ConversationDto sent in the conversation.added realtime event to a newly-added group member. */
  async findById(conversationId: string): Promise<ConversationRow | null> {
    const { rows } = await this.pool.query<ConversationRow>(
      `SELECT * FROM conversations WHERE id = $1`,
      [conversationId],
    );
    return rows[0] ?? null;
  }

  async isMember(conversationId: string, userId: string): Promise<boolean> {
    const { rows } = await this.pool.query(
      `SELECT 1 FROM conversation_members WHERE conversation_id = $1 AND user_id = $2`,
      [conversationId, userId],
    );
    return rows.length > 0;
  }

  /** Other members of a conversation, excluding the given user — the realtime broadcast target list. */
  async listOtherMemberIds(conversationId: string, excludingUserId: string): Promise<string[]> {
    const { rows } = await this.pool.query<{ user_id: string }>(
      `SELECT user_id FROM conversation_members WHERE conversation_id = $1 AND user_id != $2`,
      [conversationId, excludingUserId],
    );
    return rows.map((r) => r.user_id);
  }

  /**
   * Every current member id, including the caller. Used for group-change
   * realtime broadcasts (Phase 6), which need the full remaining member
   * list rather than "everyone but the actor" — the actor's own other
   * connected devices need the event too.
   */
  async listMemberIds(conversationId: string): Promise<string[]> {
    const { rows } = await this.pool.query<{ user_id: string }>(
      `SELECT user_id FROM conversation_members WHERE conversation_id = $1`,
      [conversationId],
    );
    return rows.map((r) => r.user_id);
  }

  /**
   * Creates a group conversation and every membership row (creator as
   * 'owner', everyone else as 'member') atomically — same all-or-nothing
   * shape as createDirect above. memberIds is expected to already exclude
   * the creator and be de-duplicated (see ConversationsService.createGroup);
   * this method doesn't re-check either, since it's private to that one
   * call site.
   */
  async createGroup(creatorId: string, title: string, memberIds: string[]): Promise<ConversationRow> {
    const client: PoolClient = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const { rows } = await client.query<ConversationRow>(
        `INSERT INTO conversations (kind, title, created_by) VALUES ('group', $1, $2) RETURNING *`,
        [title, creatorId],
      );
      const conversation = rows[0];
      await client.query(
        `INSERT INTO conversation_members (conversation_id, user_id, role) VALUES ($1, $2, 'owner')`,
        [conversation.id, creatorId],
      );
      for (const memberId of memberIds) {
        await client.query(
          `INSERT INTO conversation_members (conversation_id, user_id, role) VALUES ($1, $2, 'member')`,
          [conversation.id, memberId],
        );
      }
      await client.query('COMMIT');
      return conversation;
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async listMembers(conversationId: string): Promise<MemberRow[]> {
    const { rows } = await this.pool.query<MemberRow>(
      `SELECT cm.user_id, cm.role, u.phone_e164, u.display_name
       FROM conversation_members cm
       JOIN users u ON u.id = cm.user_id
       WHERE cm.conversation_id = $1
       ORDER BY cm.joined_at ASC`,
      [conversationId],
    );
    return rows;
  }

  async getRole(conversationId: string, userId: string): Promise<string | null> {
    const { rows } = await this.pool.query<{ role: string }>(
      `SELECT role FROM conversation_members WHERE conversation_id = $1 AND user_id = $2`,
      [conversationId, userId],
    );
    return rows[0]?.role ?? null;
  }

  /** ON CONFLICT DO NOTHING — adding someone who's already a member is a harmless no-op, not an error (the PK on (conversation_id, user_id) is what makes this safe). */
  async addMembers(conversationId: string, userIds: string[]): Promise<void> {
    for (const userId of userIds) {
      await this.pool.query(
        `INSERT INTO conversation_members (conversation_id, user_id, role) VALUES ($1, $2, 'member')
         ON CONFLICT (conversation_id, user_id) DO NOTHING`,
        [conversationId, userId],
      );
    }
  }

  /**
   * A hard delete of the membership row, not a soft-remove flag — unlike
   * messages.deleted_at (kept as a placeholder so history has no gap),
   * there's no "this person used to be a member" UI to preserve here.
   * Past messages they sent keep their sender_id FK regardless (users
   * aren't deleted, only their membership row is).
   */
  async removeMember(conversationId: string, userId: string): Promise<void> {
    await this.pool.query(
      `DELETE FROM conversation_members WHERE conversation_id = $1 AND user_id = $2`,
      [conversationId, userId],
    );
  }

  async updateTitle(conversationId: string, title: string): Promise<void> {
    await this.pool.query(`UPDATE conversations SET title = $2 WHERE id = $1`, [conversationId, title]);
  }

  async isGroup(conversationId: string): Promise<boolean> {
    const { rows } = await this.pool.query<{ kind: string }>(
      `SELECT kind FROM conversations WHERE id = $1`,
      [conversationId],
    );
    return rows[0]?.kind === 'group';
  }
}
