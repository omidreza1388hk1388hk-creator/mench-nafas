import { Inject, Injectable } from '@nestjs/common';
import { Pool, PoolClient } from 'pg';
import { PG_POOL } from '../../database/database.module';
import { GroupSettings, Who } from './group-policy';

export interface ConversationRow {
  id: string;
  kind: string;
  title: string | null;
  created_by: string | null;
  created_at: Date;
  last_message_at: Date | null;
  /** Present only on rows from listForUser/findForMember (the requesting member's own settings). */
  is_muted?: boolean;
  is_pinned?: boolean;
  is_archived?: boolean;
  together_since?: string | null;
  pinned_message_id?: string | null;
  next_meet_date?: string | null;
}

export interface MemberRow {
  user_id: string;
  phone_e164: string;
  display_name: string | null;
  role: string;
  avatar_version?: string | null;
}

export interface OtherMemberRow {
  id: string;
  phone_e164: string;
  display_name: string | null;
  avatar_version?: string | null;
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

  /** The caller's own Saved messages conversation (with their per-user settings), or null if not created yet. */
  async findSavedFor(userId: string): Promise<ConversationRow | null> {
    const { rows } = await this.pool.query<ConversationRow>(
      `SELECT c.*, m.is_muted, m.is_pinned, m.is_archived
         FROM conversations c
         JOIN conversation_members m ON m.conversation_id = c.id AND m.user_id = $1
        WHERE c.kind = 'saved'
        LIMIT 1`,
      [userId],
    );
    return rows[0] ?? null;
  }

  /** Creates the one-member saved conversation. The unique index makes a concurrent double-create fail instead of duplicating. */
  async createSaved(userId: string): Promise<ConversationRow> {
    const client: PoolClient = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const { rows } = await client.query<ConversationRow>(
        `INSERT INTO conversations (kind, created_by) VALUES ('saved', $1) RETURNING *`,
        [userId],
      );
      await client.query(
        `INSERT INTO conversation_members (conversation_id, user_id) VALUES ($1, $2)`,
        [rows[0].id, userId],
      );
      await client.query('COMMIT');
      return rows[0];
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  /** Phase 8: per-member mute/pin. Only the provided fields change. */
  async updateMemberSettings(
    conversationId: string,
    userId: string,
    settings: { isMuted?: boolean; isPinned?: boolean; isArchived?: boolean },
  ): Promise<void> {
    await this.pool.query(
      `UPDATE conversation_members
          SET is_muted = COALESCE($3, is_muted),
              is_pinned = COALESCE($4, is_pinned),
              is_archived = COALESCE($5, is_archived)
        WHERE conversation_id = $1 AND user_id = $2`,
      [
        conversationId,
        userId,
        settings.isMuted ?? null,
        settings.isPinned ?? null,
        settings.isArchived ?? null,
      ],
    );
  }

  /** Phase 9: set this member's mood (stamped now). */
  async setMood(
    conversationId: string,
    userId: string,
    emoji: string,
    text: string | null,
  ): Promise<void> {
    await this.pool.query(
      `UPDATE conversation_members
          SET mood_emoji = $3, mood_text = $4, mood_set_at = now()
        WHERE conversation_id = $1 AND user_id = $2`,
      [conversationId, userId, emoji, text],
    );
  }

  async listMoods(conversationId: string): Promise<
    {
      user_id: string;
      mood_emoji: string | null;
      mood_text: string | null;
      mood_set_at: Date | null;
    }[]
  > {
    const { rows } = await this.pool.query(
      `SELECT user_id, mood_emoji, mood_text, mood_set_at FROM conversation_members WHERE conversation_id = $1`,
      [conversationId],
    );
    return rows;
  }

  /** Phase 11: pin (or clear, with null) one message for everyone in the conversation. */
  async setPinnedMessage(conversationId: string, messageId: string | null): Promise<void> {
    await this.pool.query('UPDATE conversations SET pinned_message_id = $2 WHERE id = $1', [
      conversationId,
      messageId,
    ]);
  }

  async messageBelongsToConversation(messageId: string, conversationId: string): Promise<boolean> {
    const { rows } = await this.pool.query(
      'SELECT 1 FROM messages WHERE id = $1 AND conversation_id = $2',
      [messageId, conversationId],
    );
    return rows.length > 0;
  }

  /** Phase 9: shared dates. `undefined` leaves a field alone; `null` clears it. */
  async updateUsDates(
    conversationId: string,
    dates: { togetherSince?: string | null; nextMeetDate?: string | null },
  ): Promise<void> {
    await this.pool.query(
      `UPDATE conversations
          SET together_since = CASE WHEN $2::boolean THEN $3 ELSE together_since END,
              next_meet_date = CASE WHEN $4::boolean THEN $5 ELSE next_meet_date END
        WHERE id = $1`,
      [
        conversationId,
        dates.togetherSince !== undefined,
        dates.togetherSince ?? null,
        dates.nextMeetDate !== undefined,
        dates.nextMeetDate ?? null,
      ],
    );
  }

  /** Phase 8: which members of this conversation have it muted (used to suppress their push notifications). */
  async listMutedMemberIds(conversationId: string): Promise<string[]> {
    const { rows } = await this.pool.query<{ user_id: string }>(
      `SELECT user_id FROM conversation_members WHERE conversation_id = $1 AND is_muted = TRUE`,
      [conversationId],
    );
    return rows.map((r) => r.user_id);
  }

  async listForUser(userId: string): Promise<ConversationRow[]> {
    const { rows } = await this.pool.query<ConversationRow>(
      `SELECT c.*, m.is_muted, m.is_pinned, m.is_archived FROM conversations c
       JOIN conversation_members m ON m.conversation_id = c.id
       WHERE m.user_id = $1
       ORDER BY m.is_pinned DESC, c.last_message_at DESC NULLS LAST, c.created_at DESC`,
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
  async getOtherMember(
    conversationId: string,
    excludingUserId: string,
  ): Promise<OtherMemberRow | null> {
    const { rows } = await this.pool.query<OtherMemberRow>(
      `SELECT u.id, u.phone_e164, u.display_name, u.avatar_version
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
      `SELECT c.id AS conversation_id, u.id, u.phone_e164, u.display_name, u.avatar_version
       FROM conversations c
       JOIN conversation_members mine ON mine.conversation_id = c.id AND mine.user_id = $1
       JOIN conversation_members other ON other.conversation_id = c.id AND other.user_id != $1
       JOIN users u ON u.id = other.user_id
       WHERE c.kind = 'direct'`,
      [userId],
    );
    const map = new Map<string, OtherMemberRow>();
    for (const row of rows) {
      map.set(row.conversation_id, {
        id: row.id,
        phone_e164: row.phone_e164,
        display_name: row.display_name,
        avatar_version: row.avatar_version,
      });
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
  async createGroup(
    creatorId: string,
    title: string,
    memberIds: string[],
  ): Promise<ConversationRow> {
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
      `SELECT cm.user_id, cm.role, u.phone_e164, u.display_name, u.avatar_version
       FROM conversation_members cm
       JOIN users u ON u.id = cm.user_id
       WHERE cm.conversation_id = $1
       ORDER BY cm.joined_at ASC`,
      [conversationId],
    );
    return rows;
  }

  // ---- Phase 2A: group governance ----

  async getGroupSettings(conversationId: string): Promise<GroupSettings | null> {
    const { rows } = await this.pool.query<{
      send_permission: Who;
      add_members_permission: Who;
      edit_info_permission: Who;
      pin_permission: Who;
      slow_mode_seconds: number;
    }>(
      `SELECT send_permission, add_members_permission, edit_info_permission, pin_permission, slow_mode_seconds
       FROM conversations WHERE id = $1`,
      [conversationId],
    );
    const r = rows[0];
    if (!r) return null;
    return {
      sendMessages: r.send_permission,
      addMembers: r.add_members_permission,
      editInfo: r.edit_info_permission,
      pinMessages: r.pin_permission,
      slowModeSeconds: r.slow_mode_seconds,
    };
  }

  /** Whitelisted columns only — the keys come from parseSettingsPatch, never raw client input. */
  async updateGroupSettings(conversationId: string, patch: Partial<GroupSettings>): Promise<void> {
    const columns: Record<keyof GroupSettings, string> = {
      sendMessages: 'send_permission',
      addMembers: 'add_members_permission',
      editInfo: 'edit_info_permission',
      pinMessages: 'pin_permission',
      slowModeSeconds: 'slow_mode_seconds',
    };
    const sets: string[] = [];
    const values: unknown[] = [conversationId];
    for (const key of Object.keys(patch) as (keyof GroupSettings)[]) {
      values.push(patch[key]);
      sets.push(`${columns[key]} = $${values.length}`);
    }
    if (sets.length === 0) return;
    await this.pool.query(`UPDATE conversations SET ${sets.join(', ')} WHERE id = $1`, values);
  }

  /** Hands the group to another member in one atomic statement: target becomes owner, previous owner becomes admin. */
  async transferOwnership(
    conversationId: string,
    fromUserId: string,
    toUserId: string,
  ): Promise<void> {
    await this.pool.query(
      `UPDATE conversation_members
          SET role = CASE WHEN user_id = $3 THEN 'owner' ELSE 'admin' END
        WHERE conversation_id = $1 AND user_id IN ($2, $3)`,
      [conversationId, fromUserId, toUserId],
    );
  }

  async setRole(conversationId: string, userId: string, role: 'admin' | 'member'): Promise<void> {
    await this.pool.query(
      `UPDATE conversation_members SET role = $3 WHERE conversation_id = $1 AND user_id = $2`,
      [conversationId, userId, role],
    );
  }

  /**
   * Everything MessagesService.send needs to enforce channel/slow-mode rules in one query. The member's previous
   * message time excludes the message being (re)sent, so a retried delivery of an already-accepted message is not
   * mistaken for a second message inside the slow-mode window.
   */
  async getSendContext(
    conversationId: string,
    userId: string,
    clientMsgId: string,
  ): Promise<{
    kind: string;
    role: string;
    sendMessages: Who;
    slowModeSeconds: number;
    lastSentAt: Date | null;
  } | null> {
    const { rows } = await this.pool.query<{
      kind: string;
      role: string;
      send_permission: Who;
      slow_mode_seconds: number;
      last_sent_at: Date | null;
    }>(
      `SELECT c.kind, cm.role, c.send_permission, c.slow_mode_seconds,
              (SELECT MAX(m.created_at) FROM messages m
                 WHERE m.conversation_id = c.id AND m.sender_id = $2 AND m.client_msg_id <> $3::uuid) AS last_sent_at
       FROM conversations c
       JOIN conversation_members cm ON cm.conversation_id = c.id AND cm.user_id = $2
       WHERE c.id = $1`,
      [conversationId, userId, clientMsgId],
    );
    const r = rows[0];
    if (!r) return null;
    return {
      kind: r.kind,
      role: r.role,
      sendMessages: r.send_permission,
      slowModeSeconds: r.slow_mode_seconds,
      lastSentAt: r.last_sent_at,
    };
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
    await this.pool.query(`UPDATE conversations SET title = $2 WHERE id = $1`, [
      conversationId,
      title,
    ]);
  }

  async isGroup(conversationId: string): Promise<boolean> {
    const { rows } = await this.pool.query<{ kind: string }>(
      `SELECT kind FROM conversations WHERE id = $1`,
      [conversationId],
    );
    return rows[0]?.kind === 'group';
  }
}
