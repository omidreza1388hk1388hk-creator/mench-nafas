import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';
import { PollDto } from './poll.dto';

interface PollRow {
  id: string;
  conversation_id: string;
  creator_id: string;
  question: string;
  multiple: boolean;
  closed_at: Date | null;
  created_at: Date;
}

interface OptionRow {
  id: string;
  text: string;
}

interface VoteRow {
  option_id: string;
  user_id: string;
}

@Injectable()
export class PollsRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(input: {
    conversationId: string;
    creatorId: string;
    question: string;
    multiple: boolean;
    options: string[];
  }): Promise<string> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const { rows } = await client.query<{ id: string }>(
        `INSERT INTO polls (conversation_id, creator_id, question, multiple) VALUES ($1, $2, $3, $4) RETURNING id`,
        [input.conversationId, input.creatorId, input.question, input.multiple],
      );
      const pollId = rows[0].id;
      for (let i = 0; i < input.options.length; i++) {
        await client.query(
          `INSERT INTO poll_options (poll_id, position, text) VALUES ($1, $2, $3)`,
          [pollId, i, input.options[i]],
        );
      }
      await client.query('COMMIT');
      return pollId;
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async findDto(pollId: string): Promise<PollDto | null> {
    const { rows } = await this.pool.query<PollRow>(`SELECT * FROM polls WHERE id = $1`, [pollId]);
    const poll = rows[0];
    if (!poll) return null;
    const options = await this.pool.query<OptionRow>(
      `SELECT id, text FROM poll_options WHERE poll_id = $1 ORDER BY position`,
      [pollId],
    );
    const votes = await this.pool.query<VoteRow>(
      `SELECT option_id, user_id FROM poll_votes WHERE poll_id = $1 ORDER BY created_at`,
      [pollId],
    );
    const byOption = new Map<string, string[]>();
    for (const v of votes.rows) {
      const list = byOption.get(v.option_id) ?? [];
      list.push(v.user_id);
      byOption.set(v.option_id, list);
    }
    return {
      id: poll.id,
      conversationId: poll.conversation_id,
      creatorId: poll.creator_id,
      question: poll.question,
      multiple: poll.multiple,
      closed: poll.closed_at !== null,
      createdAt: poll.created_at.toISOString(),
      options: options.rows.map((o) => ({
        id: o.id,
        text: o.text,
        voterIds: byOption.get(o.id) ?? [],
      })),
    };
  }

  async listOptionIds(pollId: string): Promise<string[]> {
    const { rows } = await this.pool.query<{ id: string }>(
      `SELECT id FROM poll_options WHERE poll_id = $1`,
      [pollId],
    );
    return rows.map((r) => r.id);
  }

  /** Replaces all of one user's votes on a poll in a single transaction. */
  async replaceVotes(pollId: string, userId: string, optionIds: string[]): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query(`DELETE FROM poll_votes WHERE poll_id = $1 AND user_id = $2`, [
        pollId,
        userId,
      ]);
      for (const optionId of optionIds) {
        await client.query(
          `INSERT INTO poll_votes (poll_id, option_id, user_id) VALUES ($1, $2, $3)`,
          [pollId, optionId, userId],
        );
      }
      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  }

  async close(pollId: string): Promise<void> {
    await this.pool.query(`UPDATE polls SET closed_at = COALESCE(closed_at, now()) WHERE id = $1`, [
      pollId,
    ]);
  }
}
