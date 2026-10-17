import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface InviteRow {
  id: string;
  conversation_id: string;
  code: string;
  created_by: string;
  expires_at: Date | null;
  max_uses: number | null;
  uses: number;
  revoked_at: Date | null;
  created_at: Date;
}

@Injectable()
export class GroupAdminRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async createInvite(input: { conversationId: string; code: string; createdBy: string; expiresAt: Date | null; maxUses: number | null }): Promise<InviteRow> {
    const { rows } = await this.pool.query<InviteRow>(
      `INSERT INTO conversation_invites (conversation_id, code, created_by, expires_at, max_uses)
       VALUES ($1, $2, $3, $4, $5) RETURNING *`,
      [input.conversationId, input.code, input.createdBy, input.expiresAt, input.maxUses],
    );
    return rows[0];
  }

  async listInvites(conversationId: string): Promise<InviteRow[]> {
    const { rows } = await this.pool.query<InviteRow>(
      `SELECT * FROM conversation_invites WHERE conversation_id = $1 AND revoked_at IS NULL ORDER BY created_at DESC LIMIT 50`,
      [conversationId],
    );
    return rows;
  }

  async countActiveInvites(conversationId: string): Promise<number> {
    const { rows } = await this.pool.query<{ n: string }>(
      `SELECT COUNT(*) AS n FROM conversation_invites
       WHERE conversation_id = $1 AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > now())`,
      [conversationId],
    );
    return Number(rows[0].n);
  }

  async revokeInvite(conversationId: string, inviteId: string): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE conversation_invites SET revoked_at = now() WHERE id = $1 AND conversation_id = $2 AND revoked_at IS NULL`,
      [inviteId, conversationId],
    );
    return (result.rowCount ?? 0) > 0;
  }

  async findByCode(code: string): Promise<InviteRow | null> {
    const { rows } = await this.pool.query<InviteRow>(`SELECT * FROM conversation_invites WHERE code = $1`, [code]);
    return rows[0] ?? null;
  }

  /** Atomically consumes one use; false if the invite was used up / revoked / expired in the meantime. */
  async consumeUse(inviteId: string): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE conversation_invites SET uses = uses + 1
       WHERE id = $1 AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > now()) AND (max_uses IS NULL OR uses < max_uses)`,
      [inviteId],
    );
    return (result.rowCount ?? 0) > 0;
  }

  async memberCount(conversationId: string): Promise<number> {
    const { rows } = await this.pool.query<{ n: string }>(`SELECT COUNT(*) AS n FROM conversation_members WHERE conversation_id = $1`, [conversationId]);
    return Number(rows[0].n);
  }
}
