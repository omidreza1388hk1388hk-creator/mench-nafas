import { BadRequestException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { Pool } from 'pg';
import { PG_POOL } from '../../database/database.module';

export interface PlannerInput {
  kind?: 'reminder' | 'task' | 'event';
  title: string;
  note?: string;
  dueAt?: string | null;
  repeat?: 'none' | 'daily' | 'weekly' | 'monthly';
  remindMinutesBefore?: number;
  location?: string;
  checklist?: Array<{ text: string; done: boolean }>;
  /** Display names of the people an event is with (max 20, 60 chars each). */
  participants?: string[];
  conversationId?: string;
  messageId?: string;
}

const toDto = (r: any) => ({
  id: r.id,
  kind: r.kind,
  title: r.title,
  note: r.note,
  dueAt: r.due_at,
  repeat: r.repeat,
  remindMinutesBefore: r.remind_minutes_before,
  location: r.location,
  checklist: r.checklist,
  participants: r.participants ?? [],
  conversationId: r.conversation_id,
  messageId: r.message_id,
  completedAt: r.completed_at,
});

@Injectable()
export class PlannerService {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  private clean(c?: Array<{ text: string; done: boolean }>) {
    return (c ?? [])
      .slice(0, 50)
      .map((i) => ({ text: String(i.text ?? '').slice(0, 200), done: !!i.done }))
      .filter((i) => i.text.length > 0);
  }

  /** Trims, de-duplicates and caps participant names. Exported through the service so the unit test can reach it. */
  cleanParticipants(p?: string[]): string[] {
    const seen = new Set<string>();
    for (const raw of p ?? []) {
      const name = String(raw ?? '')
        .trim()
        .slice(0, 60);
      if (name) seen.add(name);
      if (seen.size >= 20) break;
    }
    return [...seen];
  }

  async create(userId: string, dto: PlannerInput) {
    const title = (dto.title ?? '').trim();
    if (!title || title.length > 200)
      throw new BadRequestException('Title must be 1-200 characters');
    const due = dto.dueAt ? new Date(dto.dueAt) : null;
    if (due && isNaN(due.getTime())) throw new BadRequestException('Invalid date');
    if ((dto.kind === 'event' || (dto.repeat && dto.repeat !== 'none')) && !due)
      throw new BadRequestException('A date is required');
    // A linked message must belong to a conversation this user is in.
    if (dto.messageId) {
      const ok = await this.pool.query(
        'SELECT 1 FROM messages m JOIN conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = $2 WHERE m.id = $1',
        [dto.messageId, userId],
      );
      if (!ok.rows[0]) throw new NotFoundException('Message not found');
    }
    const { rows } = await this.pool.query(
      `INSERT INTO planner_items (user_id, kind, title, note, due_at, repeat, remind_minutes_before, location, checklist, conversation_id, message_id, participants)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) RETURNING *`,
      [
        userId,
        dto.kind ?? 'reminder',
        title,
        dto.note?.slice(0, 2000) ?? null,
        due,
        dto.repeat ?? 'none',
        Math.min(Math.max(dto.remindMinutesBefore ?? 0, 0), 10080),
        dto.location?.slice(0, 200) ?? null,
        JSON.stringify(this.clean(dto.checklist)),
        dto.conversationId ?? null,
        dto.messageId ?? null,
        JSON.stringify(this.cleanParticipants(dto.participants)),
      ],
    );
    return toDto(rows[0]);
  }

  async list(
    userId: string,
    filter: 'overdue' | 'today' | 'upcoming' | 'completed' | 'all',
    tzOffsetMinutes: number,
  ) {
    // "Today" is the user's local day: shift now by their offset, take the day bounds, shift back.
    const offsetMs = (Number.isFinite(tzOffsetMinutes) ? tzOffsetMinutes : 0) * 60_000;
    const localNow = new Date(Date.now() + offsetMs);
    const startLocal = Date.UTC(
      localNow.getUTCFullYear(),
      localNow.getUTCMonth(),
      localNow.getUTCDate(),
    );
    const dayStart = new Date(startLocal - offsetMs);
    const dayEnd = new Date(startLocal + 86400_000 - offsetMs);
    const where: Record<string, [string, unknown[]]> = {
      overdue: ['completed_at IS NULL AND due_at IS NOT NULL AND due_at < now()', []],
      today: ['completed_at IS NULL AND due_at >= $2 AND due_at < $3', [dayStart, dayEnd]],
      upcoming: ['completed_at IS NULL AND (due_at >= $2 OR due_at IS NULL)', [dayEnd]],
      completed: ['completed_at IS NOT NULL', []],
      all: ['TRUE', []],
    };
    const [clause, extra] = where[filter] ?? where.all;
    const { rows } = await this.pool.query(
      `SELECT * FROM planner_items WHERE user_id = $1 AND ${clause} ORDER BY due_at ASC NULLS LAST, created_at DESC LIMIT 300`,
      [userId, ...extra],
    );
    return rows.map(toDto);
  }

  async update(userId: string, id: string, patch: Partial<PlannerInput>) {
    const cur = await this.pool.query(
      'SELECT * FROM planner_items WHERE id = $1 AND user_id = $2',
      [id, userId],
    );
    if (!cur.rows[0]) throw new NotFoundException();
    const c = cur.rows[0];
    const due = patch.dueAt === undefined ? c.due_at : patch.dueAt ? new Date(patch.dueAt) : null;
    const { rows } = await this.pool.query(
      `UPDATE planner_items SET title=$3, note=$4, due_at=$5, repeat=$6, remind_minutes_before=$7, location=$8, checklist=$9, participants=$10, updated_at=now()
        WHERE id=$1 AND user_id=$2 RETURNING *`,
      [
        id,
        userId,
        (patch.title ?? c.title).slice(0, 200),
        patch.note === undefined ? c.note : patch.note,
        due,
        patch.repeat ?? c.repeat,
        patch.remindMinutesBefore ?? c.remind_minutes_before,
        patch.location === undefined ? c.location : patch.location,
        JSON.stringify(patch.checklist ? this.clean(patch.checklist) : c.checklist),
        JSON.stringify(
          patch.participants ? this.cleanParticipants(patch.participants) : (c.participants ?? []),
        ),
      ],
    );
    return toDto(rows[0]);
  }

  /** Completing a recurring item moves it to its next occurrence instead of closing it. */
  async complete(userId: string, id: string, done: boolean) {
    const cur = await this.pool.query(
      'SELECT * FROM planner_items WHERE id = $1 AND user_id = $2',
      [id, userId],
    );
    const c = cur.rows[0];
    if (!c) throw new NotFoundException();
    if (done && c.repeat !== 'none' && c.due_at) {
      const next = new Date(c.due_at);
      const step = () => {
        if (c.repeat === 'daily') next.setUTCDate(next.getUTCDate() + 1);
        else if (c.repeat === 'weekly') next.setUTCDate(next.getUTCDate() + 7);
        else next.setUTCMonth(next.getUTCMonth() + 1);
      };
      do {
        step();
      } while (next.getTime() <= Date.now());
      const { rows } = await this.pool.query(
        'UPDATE planner_items SET due_at = $3, updated_at = now() WHERE id = $1 AND user_id = $2 RETURNING *',
        [id, userId, next],
      );
      return toDto(rows[0]);
    }
    const { rows } = await this.pool.query(
      'UPDATE planner_items SET completed_at = CASE WHEN $3 THEN now() ELSE NULL END, updated_at = now() WHERE id = $1 AND user_id = $2 RETURNING *',
      [id, userId, done],
    );
    return toDto(rows[0]);
  }

  async remove(userId: string, id: string) {
    await this.pool.query('DELETE FROM planner_items WHERE id = $1 AND user_id = $2', [id, userId]);
  }
}
